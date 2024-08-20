package com.example.galleryconnector;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.account.LAccountEntity;
import com.example.galleryconnector.local.block.LBlockEntity;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.server.ServerRepo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";
	private final ListeningExecutorService executor;

	private LocalRepo localRepo;
	private ServerRepo serverRepo;


	public static GalleryRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GalleryRepo INSTANCE = new GalleryRepo();
	}
	private GalleryRepo() {
		executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();
	}



	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	@Nullable
	public ListenableFuture<JsonObject> getAccountProps(@NonNull UUID accountuid) {
		return executor.submit(() -> {
			//Try to get the account data from local. If it exists, return that.
			List<LAccountEntity> localAccountProps =
					localRepo.database.getAccountDao().loadByUID(accountuid);
			if(!localAccountProps.isEmpty())
				return new Gson().toJsonTree( localAccountProps.get(0) ).getAsJsonObject();


			//If the account doesn't exist locally, try to get it from the server.
			try {
				return serverRepo.accountConn.getProps(accountuid);
			} catch (SocketTimeoutException e) {
				return null;
			}
		});
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	@Nullable
	public ListenableFuture<JsonObject> getFileProps(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			//Try to get the file data from local. If it exists, return that.
			List<LFileEntity> localFileProps = localRepo.database.getFileDao().loadByUID(fileuid);
			if(!localFileProps.isEmpty())
				return new Gson().toJsonTree( localFileProps.get(0) ).getAsJsonObject();


			//If the file doesn't exist locally, try to get it from the server.
			try {
				return serverRepo.fileConn.getProps(fileuid);
			} catch (SocketTimeoutException e) {
				return null;
			}
		});
	}


	//Note: External links are not imported to the system, and should not be handled with this method.
	// Instead, their contents should be created and edited through the file creation/edit modals.

	//Import to local
	//This will be the result of a queue item, and does not interact with the queue itself.
	// Upon return, the queue will be updated.
	public ListenableFuture<JsonObject> importFileToLocal(@NonNull UUID accountuid,
														  @NonNull UUID parent, Uri source) {
		return executor.submit(() -> {
			Context context = MyApplication.getAppContext();

			//Import the file to the local system, starting with baseline file properties
			LFileEntity file = new LFileEntity(accountuid);
			try {
				localRepo.uploadFile(file, source, context);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}


			//Now that the file is imported, we need to add it to the directory
			//TODO Add the new file to parent's ordering


			return new Gson().toJsonTree(file).getAsJsonObject();
		});
	}



	public ListenableFuture<Boolean> copyFileToServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
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
		});
	}


	public ListenableFuture<Boolean> copyFileToLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
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
		});
	}



	public ListenableFuture<Boolean> deleteFileFromLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			localRepo.database.getFileDao().delete(fileuid);
			return true;
		});
	}

	public ListenableFuture<Boolean> deleteFileFromServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			serverRepo.fileConn.delete(fileuid);
			return true;
		});
	}



}



















