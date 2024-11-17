package com.example.galleryconnector.movement.old;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;


public class OldDomainAPI {
	private final File operationFile;
	private final ReentrantLock lock;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;



	public static OldDomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final OldDomainAPI INSTANCE = new OldDomainAPI();
	}
	private OldDomainAPI() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		//We need a synchronous read/write lock for the operations file
		lock = new ReentrantLock();


		Context context = MyApplication.getAppContext();
		operationFile = new File(context.getDataDir(), "operations.json");


		//Make sure the operations file exists
		if(!operationFile.exists()) {
			try {
				operationFile.createNewFile();
			} catch (Exception e) {
				throw new RuntimeException("Operation mapping file could not be created!");
			}
		}
	}


	public enum Operation {
		COPY_TO_LOCAL(1),
		REMOVE_FROM_LOCAL(2),
		COPY_TO_SERVER(4),
		REMOVE_FROM_SERVER(8);
		//FOLLOW_PARENT(16);		//Removed


		private final int flag;
		Operation(int flag) {
			this.flag = flag;
		}

		public static final int LOCAL_MASK = COPY_TO_LOCAL.flag | REMOVE_FROM_LOCAL.flag;
		public static final int SERVER_MASK = COPY_TO_SERVER.flag | REMOVE_FROM_SERVER.flag;
		public static final int MASK = LOCAL_MASK | SERVER_MASK;
	}



	public boolean queueOperation(Operation newOperation, UUID fileUID) {
		//Lock the file before we make any reads/writes
		lock.lock();

		try {
			//Read all operations in the file into a JsonObject
			JsonObject allOperations = readOps();
			//Get the stored bitmask for the fileuid
			int bitmask = getMask(allOperations, fileUID);


			//Add the new operation to the bitmask
			bitmask |= newOperation.flag;

			//If adding this flag resulted in BOTH local flags (they conflict)...
			if ((Operation.LOCAL_MASK & bitmask) == Operation.LOCAL_MASK)
				bitmask &= ~Operation.LOCAL_MASK;    //Get rid of both flags since they cancel out

			//If adding this flag resulted in BOTH server flags (they conflict)...
			if ((Operation.SERVER_MASK & bitmask) == Operation.SERVER_MASK)
				bitmask &= ~Operation.SERVER_MASK;    //Get rid of both flags since they cancel out

			//(COPY_TO_LOCAL & SERVER) and (REMOVE_FROM_LOCAL & SERVER) do not conflict or cause problems.
			//Although both removes probably means something went wrong...


			//Write the updated bitmask back to the file
			writeMask(allOperations, fileUID, bitmask);

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}

		return true;
	}

	public boolean removeOperation(Operation oldOperation, UUID fileUID) {
		//Lock the file before we make any reads/writes
		lock.lock();

		try {
			//Read all operations in the file into a JsonObject
			JsonObject allOperations = readOps();
			//Get the stored bitmask for the fileuid
			int bitmask = getMask(allOperations, fileUID);

			//Remove the old operation from the bitmask
			bitmask &= ~oldOperation.flag;
			
			//Write the updated bitmask back to the file
			writeMask(allOperations, fileUID, bitmask);

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}

		return true;
	}


	//-----------------------------------------------------------


	private JsonObject readOps() throws IOException {
		//Read all operations in the file into a JsonObject (absolute worst case is a few thousand lines)
		InputStream in = Files.newInputStream(operationFile.toPath());
		InputStreamReader reader = new InputStreamReader(in);
		return JsonParser.parseReader(reader).getAsJsonObject();
	}

	private int getMask(JsonObject allOperations, UUID fileUID) {
		//Get the stored bitmask for the fileuid (or 0 if no previous entry)
		JsonElement bitmaskElement = allOperations.get(fileUID.toString());
		return bitmaskElement != null ? bitmaskElement.getAsInt() : 0;
	}

	private void writeMask(JsonObject allOperations, UUID fileUID, int bitmask) throws IOException {
		//Write the updated bitmask back to the file
		allOperations.addProperty(fileUID.toString(), bitmask);
		try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
			out.write(allOperations.toString().getBytes());
		}
	}



	//FOR TESTING
	public int getMaskTESTING(UUID fileUID) {
		try {
			//Read all operations in the file into a JsonObject
			JsonObject allOperations = readOps();
			//Get the stored bitmask for the fileuid
			return getMask(allOperations, fileUID);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------


	//TODO Fail if no internet in between, but I guess that would just throw a normal exception
	// when running copy/remove functions
	//TODO Also remove if fileuid doesn't exist, or maybe just do nothing in the copy/remove functions
	public boolean executeAQueuedOperation() {
		//Lock the file before we make any reads/writes
		lock.lock();

		try {
			//Read all operations in the file into a JsonObject
			JsonObject allOperations = readOps();


			//Get any operation
			Set<Map.Entry<String, JsonElement>> entrySet = allOperations.entrySet();
			if(!entrySet.isEmpty()) {
				Map.Entry<String, JsonElement> entry = entrySet.iterator().next();

				//Get the ID and bitmask for that operation
				UUID fileUID = UUID.fromString(entry.getKey());
				int bitmask = entry.getValue().getAsInt();


				try {
					//If we have a COPY_TO_LOCAL operation...
					if((bitmask & Operation.COPY_TO_LOCAL.flag) > 0)
						copyFileToLocal(fileUID);

					//If we have a REMOVE_FROM_LOCAL operation...
					if((bitmask & Operation.REMOVE_FROM_LOCAL.flag) > 0)
						removeFileFromLocal(fileUID);

					//If we have a COPY_TO_SERVER operation...
					if((bitmask & Operation.COPY_TO_SERVER.flag) > 0)
						copyFileToServer(fileUID);

					//If we have a REMOVE_FROM_SERVER operation...
					if((bitmask & Operation.REMOVE_FROM_SERVER.flag) > 0)
						removeFileFromServer(fileUID);

				} catch (FileNotFoundException e) {
					//We don't really want to do anything but log, this is technically a 'success'.
					//At least as long as this wasn't somehow caused by internet issues.
				}


				//Now that we've run the operations contained in the mask, remove it
				allOperations.remove(fileUID.toString());

				//And write the updated operations list back to the file
				try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
					out.write(allOperations.toString().getBytes());
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}

		return true;
	}



	//---------------------------------------------------------------------------------------------


	//TODO These eventually need to accept a hash of the previous entry for the endpoint to be sure there
	// were no updates done while we were computing/sending this. Endpoints would need to be updated as well.

	public boolean copyFileToLocal(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the server database
		JsonObject serverFileProps = serverRepo.getFileProps(fileuid);
		if(serverFileProps.isEmpty())
			throw new FileNotFoundException("File not found in server! fileuid="+fileuid);


		//Get the blockset of the file
		Type listType = new TypeToken<List<String>>() {}.getType();
		List<String> blockset = new Gson().fromJson(serverFileProps.get("fileblocks"), listType);

		List<String> missingBlocks;
		do {
			//Find if local is missing any blocks from the server file's blockset
			missingBlocks = localRepo.getMissingBlocks(blockset);

			//For each block that local is missing...
			for(String block : missingBlocks) {
				//Read the block data from server block storage
				byte[] blockData = serverRepo.getBlockData(block);

				//And write the data to local
				localRepo.blockHandler.writeBlock(block, blockData);
			}
		} while(!missingBlocks.isEmpty());


		//Now that the blockset is uploaded, put the file metadata into the local database
		LFileEntity file = new Gson().fromJson(serverFileProps, LFileEntity.class);
		localRepo.putFileProps(file);

		return true;
	}

	//---------------------------------------------------

	public boolean copyFileToServer(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the local database
		LFileEntity file = localRepo.getFileProps(fileuid);
		if(file == null)
			throw new FileNotFoundException("File not found locally! fileuid="+fileuid);


		//Get the blockset of the file
		List<String> blockset = file.fileblocks;

		List<String> missingBlocks;
		try {
			do {
				//Find if the server is missing any blocks from the local file's blockset
				missingBlocks = serverRepo.getMissingBlocks(blockset);

				//For each block the server is missing...
				for(String block : missingBlocks) {
					//Read the block data from local block storage
					byte[] blockData = localRepo.blockHandler.readBlock(block);

					//And upload the data to the server
					serverRepo.blockConn.uploadData(block, blockData);
				}
			} while(!missingBlocks.isEmpty());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		//Now that the blockset is uploaded, create/update the file metadata
		JsonObject fileProps = new Gson().toJsonTree(file).getAsJsonObject();
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
