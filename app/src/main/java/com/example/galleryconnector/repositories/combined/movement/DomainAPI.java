package com.example.galleryconnector.repositories.combined.movement;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;


public class DomainAPI {
	private static final String TAG = "Gal.DomAPI";
	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	//TODO Persist this somehow, maybe with shared prefs, maybe with https://square.github.io/tape/
	private final BlockingQueue<UUID> queue;
	private final Map<UUID, Integer> operationsMap;


	public static DomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DomainAPI INSTANCE = new DomainAPI();
	}
	private DomainAPI() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		//Could use a Deque if we wanted to add to the front for priority queueing
		queue = new LinkedBlockingQueue<>();
		operationsMap = new HashMap<>();
	}


	public static final int COPY_TO_LOCAL = 1;
	public static final int REMOVE_FROM_LOCAL = 2;
	public static final int COPY_TO_SERVER = 4;
	public static final int REMOVE_FROM_SERVER = 8;
	public static final int LOCAL_MASK = COPY_TO_LOCAL | REMOVE_FROM_LOCAL;
	public static final int SERVER_MASK = COPY_TO_SERVER | REMOVE_FROM_SERVER;
	public static final int MASK = LOCAL_MASK | SERVER_MASK;




	//Actually launches a worker to execute the operation on the file
	public void doSomething(int times) throws InterruptedException {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		for(int i = 0; i < times; i++) {
			if(queue.isEmpty())
				return;

			//Get the ID of the next file in line
			UUID nextFile = queue.take();
			if(nextFile == null) {
				Log.w(TAG, "Null file ID in queue!");
				continue;
			}

			//Get the operations for the next file in line
			Integer operationsMask = operationsMap.get(nextFile);

			//If there are no operations for the next file, it was dequeued and should be skipped
			if(operationsMask == null)
				continue;

			//Launch the worker to perform the operation
			WorkRequest request = buildWorker(nextFile, operationsMask).build();
			workManager.enqueue(request);
		}
	}


	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileuid, @NonNull Integer operationsMask) {
		Data.Builder data = new Data.Builder();
		data.putString("OPERATIONS", operationsMask.toString());
		data.putString("FILEUID", fileuid.toString());

		return new OneTimeWorkRequest.Builder(DomainOpWorker.class)
				.setInputData(data.build())
				.addTag(fileuid.toString())
				.addTag(operationsMask.toString());
	}


	//---------------------------------------------------------------------------------------------

	public void enqueue(@NonNull UUID fileuid, @NonNull Integer... newOperations) throws InterruptedException {
		//Get any current operations for this file
		Integer operationsMask = operationsMap.getOrDefault(fileuid, 0);

		//Add all operations to the existing mask
		for(Integer operation : newOperations) {
			operationsMask |= operation;
		}


		//Look for any conflicting operations and, if found, remove both.
		//E.g. This operations mask now contains both COPY_TO_LOCAL and REMOVE_FROM_LOCAL.
		//Copying a file to local just to remove it would be redundant, so we can safely remove both.

		if((operationsMask & LOCAL_MASK) == LOCAL_MASK)
			operationsMask &= ~(LOCAL_MASK);
		else if((operationsMask & SERVER_MASK) == SERVER_MASK)
			operationsMask &= ~(SERVER_MASK);


		boolean isAlreadyQueued = operationsMap.containsKey(fileuid);

		//Add the new operations mask to the map
		operationsMap.put(fileuid, operationsMask);

		//And queue the file for movement if it is not already queued
		if(!isAlreadyQueued) queue.put(fileuid);
	}


	//---------------------------------------------------

	/** @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid, @NonNull Integer... operations) {
		Integer operationsMask = operationsMap.get(fileuid);
		if(operationsMask == null) return false;

		//Remove all specified operations
		for(Integer operation : operations)
			operationsMask &= ~operation;

		//If there are no operations left to perform, remove the file from the mapping
		if(operationsMask == 0)
			operationsMap.remove(fileuid);

		//Otherwise, update the operations mask
		operationsMap.put(fileuid, operationsMask);
		return true;
	}
	/**  @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid) {
		if(!operationsMap.containsKey(fileuid)) return false;

		operationsMap.remove(fileuid);
		return true;
	}



	//---------------------------------------------------------------------------------------------


	//TODO These eventually need to accept a hash of the previous entry for the endpoint to be sure there
	// were no updates done while we were computing/sending this. Endpoints would need to be updated as well.

	public boolean copyFileToLocal(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the server database
		SFile serverFileProps;
		try {
			serverFileProps = serverRepo.getFileProps(fileuid);
		} catch (FileNotFoundException e) {
			throw new FileNotFoundException("File not found in server! fileuid="+fileuid);
		}


		//Get the blockset of the file
		List<String> blockset = serverFileProps.fileblocks;

		List<String> missingBlocks;
		do {
			//Find if local is missing any blocks from the server file's blockset
			missingBlocks = blockset.stream()
					.filter( b -> !localRepo.getBlockPropsExist(b) )
					.collect(Collectors.toList());

			//For each block that local is missing...	//TODO Parallelize these sorts of things
			for(String block : missingBlocks) {
				//Read the block data from server block storage
				byte[] blockData = serverRepo.getBlockContents(block);

				//And write the data to local
				localRepo.putBlockContents(blockData);
			}
		} while(!missingBlocks.isEmpty());


		//Now that the blockset is uploaded, put the file metadata into the local database
		LFile file = new Gson().fromJson(serverFileProps.toJson(), LFile.class);
		localRepo.putFileProps(file);

		return true;
	}

	//---------------------------------------------------

	public boolean copyFileToServer(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the local database
		LFile file = localRepo.getFileProps(fileuid);
		if(file == null)
			throw new FileNotFoundException("File not found locally! fileuid="+fileuid);


		//Get the blockset of the file
		List<String> blockset = file.fileblocks;

		List<String> missingBlocks;
		try {
			do {
				//Find if the server is missing any blocks from the local file's blockset
				missingBlocks = blockset.stream()
						.filter( b -> !serverRepo.getBlockPropsExist(b) )
						.collect(Collectors.toList());

				//For each block the server is missing...
				for(String block : missingBlocks) {
					//Read the block data from local block storage
					byte[] blockData = localRepo.getBlockContents(block);

					//And upload the data to the server
					serverRepo.putBlockContents(blockData);
				}
			} while(!missingBlocks.isEmpty());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		//Now that the blockset is uploaded, create/update the file metadata
		SFile fileProps = new Gson().fromJson(file.toJson(), SFile.class);
		serverRepo.putFileProps(fileProps);

		return true;
	}


	//---------------------------------------------------


	public boolean removeFileFromLocal(@NonNull UUID fileuid) {
		localRepo.database.getFileDao().delete(fileuid);
		return true;
	}

	public boolean removeFileFromServer(@NonNull UUID fileuid) throws IOException {
		serverRepo.fileConn.delete(fileuid);
		return true;
	}
}
