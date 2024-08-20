package com.example.galleryconnector;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.account.LAccountEntity;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.server.ServerRepo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
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
			List<LAccountEntity> localAccountProps = localRepo.database.getAccountDao().loadByUID(accountuid);
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


	//Import to local
	//This will be the result of a queue item, and does not interact with the queue itself. Upon return, the queue will be updated.
	public ListenableFuture<JsonObject> importFile(@NonNull UUID parent, Uri source) {
		return executor.submit(() -> {
			Context context = MyApplication.getAppContext();

			throw new RuntimeException("Stub!");
		});
	}



	public ListenableFuture<Boolean> copyFileToServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			//Get the file properties from the local database
			List<LFileEntity> localFileProps = localRepo.database.getFileDao().loadByUID(fileuid);
			if(localFileProps.isEmpty())
				throw new IllegalStateException("File not found locally! fileuid="+fileuid);
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





}



















