package com.example.galleryconnector.movement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkQuery;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


public class DomainAPI {
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
	}


	public enum Operation {
		COPY_TO_LOCAL(1),
		REMOVE_FROM_LOCAL(2),
		COPY_TO_SERVER(4),
		REMOVE_FROM_SERVER(8);


		private final int flag;
		Operation(int flag) {
			this.flag = flag;
		}

		public static final int LOCAL_MASK = COPY_TO_LOCAL.flag | REMOVE_FROM_LOCAL.flag;
		public static final int SERVER_MASK = COPY_TO_SERVER.flag | REMOVE_FROM_SERVER.flag;
		public static final int MASK = LOCAL_MASK | SERVER_MASK;


		public Operation getOpposite() {
			switch (this) {
				case COPY_TO_LOCAL: return REMOVE_FROM_LOCAL;
				case REMOVE_FROM_LOCAL: return COPY_TO_LOCAL;
				case COPY_TO_SERVER: return REMOVE_FROM_SERVER;
				case REMOVE_FROM_SERVER: return COPY_TO_SERVER;
			}
			throw new RuntimeException("Invalid operation? "+this);
		}
	}



	//TODO Make sure when these are queued, they're appended AFTER any existing work for the file
	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileuid, @NonNull Operation operation) {
		Data.Builder data = new Data.Builder();
		data.putString("OPERATION", operation.toString());
		data.putString("FILEUID", fileuid.toString());

		return new OneTimeWorkRequest.Builder(DomainOpWorker.class)
				.setInputData(data.build())
				.addTag(fileuid.toString())
				.addTag(operation.toString());
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
		localRepo.putFile(file);

		return true;
	}

	//---------------------------------------------------

	public boolean copyFileToServer(@NonNull UUID fileuid) throws IOException {
		//Get the file properties from the local database
		LFileEntity file = localRepo.getFile(fileuid);
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
