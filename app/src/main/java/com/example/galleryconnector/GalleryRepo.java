package com.example.galleryconnector;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.example.galleryconnector.movement.ImportExportWorker;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.account.LAccountEntity;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.movement.DomainAPI;
import com.example.galleryconnector.movement.ImportExportApi;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";
	private final ListeningExecutorService executor;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;

	private final GFileUpdateObservers observers;


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

		domainAPI = DomainAPI.getInstance();

		observers = new GFileUpdateObservers(localRepo, serverRepo);
	}

	//---------------------------------------------------------------------------------------------

	public void addObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.removeObserver(observer);
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
			LFileEntity localFileProps = localRepo.database.getFileDao().loadByUID(fileuid);
			if(localFileProps != null)
				return new Gson().toJsonTree( localFileProps ).getAsJsonObject();


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


	private final String IMPORT_GROUP = "import";
	private final String EXPORT_GROUP = "export";

	//Note: External links are not imported to the system, and should not be handled with this method.
	// Instead, their link file should be created and edited through the file creation/edit modals.


	//Launch a WorkManager to import an external uri to the system.
	public void importFile(@NonNull Uri source, @NonNull UUID accountuid, @NonNull UUID parent) {
		//Compile the information we'll need for the import
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "IMPORT");
		builder.putString("TARGET_URI", source.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("ACCOUNTUID", accountuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(IMPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}

	public void exportFile(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		//Compile the information we'll need for the export
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "EXPORT");
		builder.putString("TARGET_URI", destination.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("FILEUID", fileuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(EXPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}


	//---------------------------------------------------------------------------------------------
	// Domain Movements
	//---------------------------------------------------------------------------------------------

	//TODO We never actually run these
	// We should queue what we need, then call a runOps method from that og function that queued these

	public ListenableFuture<Boolean> copyFileToLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_LOCAL, fileuid);
		});
	}
	public ListenableFuture<Boolean> copyFileToServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_SERVER, fileuid);
		});
	}

	protected ListenableFuture<Boolean> copyFileToLocalImmediate(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.copyFileToLocal(fileuid);
		});
	}
	protected ListenableFuture<Boolean> copyFileToServerImmediate(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.copyFileToServer(fileuid);
		});
	}


	public ListenableFuture<Boolean> removeFileFromLocal(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.queueOperation(DomainAPI.Operation.REMOVE_FROM_LOCAL, fileuid);
		});
	}
	public ListenableFuture<Boolean> removeFileFromServer(@NonNull UUID fileuid) {
		return executor.submit(() -> {
			return domainAPI.queueOperation(DomainAPI.Operation.REMOVE_FROM_SERVER, fileuid);
		});
	}
}



















