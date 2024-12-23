package com.example.galleryconnector.repositories.combined.movement;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.PersistedMapQueue;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	private final PersistedMapQueue<UUID, Integer> pendingOperations;


	public static DomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DomainAPI INSTANCE = new DomainAPI();
	}
	private DomainAPI() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();


		String appDataDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		Path persistLocation = Paths.get(appDataDir, "queues", "domainOpQueue.txt");

		pendingOperations = new PersistedMapQueue<UUID, Integer>(persistLocation) {
			@Override
			public UUID parseKey(String keyString) { return UUID.fromString(keyString); }
			@Override
			public Integer parseVal(String valString) { return Integer.parseInt(valString); }
		};
	}
	


	public static final int COPY_TO_LOCAL = 1;
	public static final int REMOVE_FROM_LOCAL = 2;
	public static final int COPY_TO_SERVER = 4;
	public static final int REMOVE_FROM_SERVER = 8;
	public static final int LOCAL_MASK = COPY_TO_LOCAL | REMOVE_FROM_LOCAL;
	public static final int SERVER_MASK = COPY_TO_SERVER | REMOVE_FROM_SERVER;
	public static final int MASK = LOCAL_MASK | SERVER_MASK;




	//Launches N workers to execute the next N operations (if available)
	//Returns the number of operations launched
	public int doSomething(int times) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Get the next N fileUID and operation pairs
		List<Map.Entry<UUID, Integer>> nextOperations = pendingOperations.pop(times);

		for(Map.Entry<UUID, Integer> entry : nextOperations) {
			UUID fileUID = entry.getKey();
			Integer operationsMask = entry.getValue();

			if(fileUID == null) {
				Log.w(TAG, "Null file ID in queue!");
				continue;
			}

			//If there are no operations for the file, it should be skipped
			if(operationsMask == null)
				continue;


			//Launch the worker to perform the operation
			WorkRequest request = buildWorker(fileUID, operationsMask).build();
			workManager.enqueue(request);
		}

		return nextOperations.size();
	}


	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileuid, @NonNull Integer operationsMask) {
		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileuid.toString());
		data.putString("OPERATIONS", operationsMask.toString());

		return new OneTimeWorkRequest.Builder(DomainOpWorker.class)
				.setInputData(data.build())
				.addTag(fileuid.toString())
				.addTag(operationsMask.toString());
	}


	//---------------------------------------------------------------------------------------------

	public void enqueue(@NonNull UUID fileuid, @NonNull Integer... newOperations) throws InterruptedException {
		//Get any current operations for this file
		Integer operationsMask = pendingOperations.getOrDefault(fileuid, 0);

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


		//Queue the updated operations
		pendingOperations.enqueue(fileuid, operationsMask);
	}


	//---------------------------------------------------

	/** @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid, @NonNull Integer... operations) {
		Integer operationsMask = pendingOperations.get(fileuid);
		if(operationsMask == null) return false;

		//Remove all specified operations
		for(Integer operation : operations)
			operationsMask &= ~operation;

		//If there are no operations left to perform, remove the file from the mapping
		if(operationsMask == 0)
			pendingOperations.dequeue(fileuid);

		//Otherwise, update the operations mask
		pendingOperations.enqueue(fileuid, operationsMask);
		return true;
	}
	/**  @return True if operations were removed, false if there were no operations to remove */
	public boolean dequeue(@NonNull UUID fileuid) {
		if(!pendingOperations.containsKey(fileuid)) return false;

		pendingOperations.dequeue(fileuid);
		return true;
	}



	//=============================================================================================
	// API
	//=============================================================================================

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

		//And download them all from the server
		copyBlocksToLocal(blockset);


		//Now that the blockset is uploaded, put the file metadata into the local database
		LFile file = new Gson().fromJson(serverFileProps.toJson(), LFile.class);
		localRepo.putFileProps(file, null, null);

		return true;
	}


	public boolean copyFileToServer(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the local database
		LFile file = localRepo.getFileProps(fileuid);
		if(file == null)
			throw new FileNotFoundException("File not found locally! fileuid="+fileuid);


		//Get the blockset of the file
		List<String> blockset = file.fileblocks;

		//And send them all to the server
		copyBlocksToServer(blockset);


		//Now that the blockset is uploaded, create/update the file metadata
		SFile fileProps = new Gson().fromJson(file.toJson(), SFile.class);
		serverRepo.putFileProps(fileProps, null, null);

		return true;
	}


	//---------------------------------------------------


	public void copyBlocksToLocal(@NonNull List<String> blockset) throws IOException {
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
	}

	public void copyBlocksToServer(@NonNull List<String> blockset) throws IOException {
		List<String> missingBlocks;
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
