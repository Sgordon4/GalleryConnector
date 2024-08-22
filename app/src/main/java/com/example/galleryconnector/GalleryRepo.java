package com.example.galleryconnector;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.account.LAccountEntity;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.movement.DomainAPI;
import com.example.galleryconnector.movement.MovementHandler;
import com.example.galleryconnector.server.ServerRepo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";
	private final ListeningExecutorService executor;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final MovementHandler movementHandler;


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

		movementHandler = MovementHandler.getInstance();
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



	//---------------------------------------------------------------------------------------------
	// Import/Export
	//---------------------------------------------------------------------------------------------


	//Note: External links are not imported to the system, and should not be handled with this method.
	// Instead, their link file should be created and edited through the file creation/edit modals.

	//Queue an import to the local filesystem. A job will pick up the queue item.
	//Upon a successful import, the file will be moved between local/server based on its parent.
	public ListenableFuture<Boolean> importFile(@NonNull Uri source,
												@NonNull UUID accountuid, @NonNull UUID parent) {
		return executor.submit(() -> {
			return movementHandler.ioAPI.queueImportFile(source, parent, accountuid);
		});
	}

	public ListenableFuture<Boolean> exportFile(@NonNull UUID fileuid, @NonNull UUID parent,
												   @NonNull Uri destination) {
		return executor.submit(() -> {
			return movementHandler.ioAPI.queueExportFile(fileuid, parent, destination);
		});
	}



	//---------------------------------------------------------------------------------------------
	// Domain Movements
	//---------------------------------------------------------------------------------------------


	public ListenableFuture<Boolean> copyFileToLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return movementHandler.domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_LOCAL, fileuid);
		});
	}
	public ListenableFuture<Boolean> copyFileToServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return movementHandler.domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_SERVER, fileuid);
		});
	}


	public ListenableFuture<Boolean> removeFileFromLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return movementHandler.domainAPI.queueOperation(DomainAPI.Operation.REMOVE_FROM_LOCAL, fileuid);
		});
	}
	public ListenableFuture<Boolean> removeFileFromServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return movementHandler.domainAPI.queueOperation(DomainAPI.Operation.REMOVE_FROM_SERVER, fileuid);
		});
	}
}



















