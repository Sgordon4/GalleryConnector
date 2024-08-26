package com.example.galleryconnector.movement;

import android.content.Context;
import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.block.LBlockEntity;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;


/*

Operation file is a json file structured as so:

{
	operations: {
		- fileuid: bitmask -
		fileuid1: 1010,
		fileuid2: 1100,
		...
	},
	queue: [
		fileuid1,
		fileuid2,
		...
	]
}

*/


public class DomainAPI {
	private final File operationFile;
	private final ReentrantLock lock;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;



	public static DomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DomainAPI INSTANCE = new DomainAPI();
	}
	private DomainAPI() {
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

				//Fill the json file with the basics
				JsonObject baseJson = new JsonObject();
				baseJson.add("operations", new JsonObject());
				baseJson.add("queue", new JsonArray());

				try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
					out.write(baseJson.toString().getBytes());
				}
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



	//TODO Lock row before read and unlock after write
	public boolean queueOperation(Operation newOperation, UUID fileUID) {
		//Lock the file before we make any reads/writes
		lock.lock();

		try {
			//Read all operations in the file into a JsonObject (absolute worst case is a few thousand lines)
			InputStream in = Files.newInputStream(operationFile.toPath());
			InputStreamReader reader = new InputStreamReader(in);
			JsonObject allOperations = JsonParser.parseReader(reader).getAsJsonObject();


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


			//Write the updated bitmask back to the file
			allOperations.addProperty(fileUID.toString(), bitmask);
			try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
				out.write(allOperations.toString().getBytes());
			}

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
			//Read all operations in the file into a JsonObject (absolute worst case is a few thousand lines)
			InputStream in = Files.newInputStream(operationFile.toPath());
			InputStreamReader reader = new InputStreamReader(in);
			JsonObject allOperations = JsonParser.parseReader(reader).getAsJsonObject();


			//Get the stored bitmask for the fileuid
			int bitmask = getMask(allOperations, fileUID);
			//Remove the old operation from the bitmask
			bitmask &= ~oldOperation.flag;


			//Write the updated bitmask back to the file
			allOperations.addProperty(fileUID.toString(), bitmask);
			try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
				out.write(allOperations.toString().getBytes());
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}

		return true;
	}

	private int getMask(JsonObject operationsFile, UUID fileUID) {
		return operationsFile.get("operations").getAsJsonObject().get(fileUID.toString()).getAsInt();
	}


	//FOR TESTING
	public int getMaskTESTING(UUID fileUID) {
		try (InputStream in = Files.newInputStream(operationFile.toPath());
			 InputStreamReader reader = new InputStreamReader(in)){

			//Read all operations in the file into a JsonObject
			JsonObject allOperations = JsonParser.parseReader(reader).getAsJsonObject();

			//Get the stored bitmask for the fileuid
			return getMask(allOperations, fileUID);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------


	public boolean executeAnOperation() {
		//Lock the file before we make any reads/writes
		lock.lock();

		try {
			//Read all operations in the file into a JsonObject (absolute worst case is a few thousand lines)
			InputStream in = Files.newInputStream(operationFile.toPath());
			InputStreamReader reader = new InputStreamReader(in);
			JsonObject allOperations = JsonParser.parseReader(reader).getAsJsonObject();


			//Get any operation
			allOperations.

			//Get the stored bitmask for the fileuid
			int bitmask = getMask(allOperations, fileUID);
			//Remove the old operation from the bitmask
			bitmask &= ~oldOperation.flag;


			//Write the updated bitmask back to the file
			allOperations.addProperty(fileUID.toString(), bitmask);
			try (OutputStream out = Files.newOutputStream(operationFile.toPath())) {
				out.write(allOperations.toString().getBytes());
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}

		return true;
	}



	public boolean copyFileToLocal(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the server database
		JsonObject serverFileProps = serverRepo.fileConn.getProps(fileuid);
		if(serverFileProps.isEmpty())
			throw new FileNotFoundException("File not found in server! fileuid="+fileuid);


		//Get the blockset of the file
		Type listType = new TypeToken<List<String>>() {}.getType();
		List<String> blockset = new Gson().fromJson(serverFileProps.get("blockset"), listType);

		List<String> missingBlocks;
		do {
			//Find if local is missing any blocks from the server file's blockset
			missingBlocks = localRepo.getMissingBlocks(blockset);

			//For each block that local is missing...
			for(String block : missingBlocks) {
				//Read the block data from server block storage
				byte[] blockData = serverRepo.blockConn.getData(block);

				//And write the data to local
				localRepo.blockHandler.writeBlock(block, blockData);
			}
		} while(!missingBlocks.isEmpty());


		//Now that the blockset is uploaded, create/update the file metadata
		LBlockEntity blockEntity = new Gson().fromJson(serverFileProps, LBlockEntity.class);
		localRepo.blockHandler.blockDao.put(blockEntity);

		return true;
	}

	//---------------------------------------------------

	public boolean copyFileToServer(@NonNull UUID fileuid) throws FileNotFoundException {
		//Get the file properties from the local database
		List<LFileEntity> localFileProps = localRepo.database.getFileDao().loadByUID(fileuid);
		if(localFileProps.isEmpty())
			throw new FileNotFoundException("File not found locally! fileuid="+fileuid);
		LFileEntity file = localFileProps.get(0);


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
		try {
			JsonObject fileProps = new Gson().toJsonTree(localFileProps).getAsJsonObject();
			serverRepo.fileConn.upsert(fileProps);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return true;
	}


	//---------------------------------------------------------------------------------------------


	public boolean removeFileFromLocal(@NonNull UUID fileuid) {
		localRepo.database.getFileDao().delete(fileuid);
		return true;
	}

	public boolean removeFileFromServer(@NonNull UUID fileuid) throws IOException {
		serverRepo.fileConn.delete(fileuid);
		return true;
	}


}
