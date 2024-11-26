package com.example.galleryconnector.repositories.combined;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.GAccount;
import com.example.galleryconnector.repositories.combined.movement.ImportExportWorker;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.account.LAccountEntity;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";
	private final ListeningExecutorService executor;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;

	private final GFileUpdateObservers observers;


	//TODO Replace all JsonObjects with Gallery POJOs


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
	public ListenableFuture<GAccount> getAccountProps(@NonNull UUID accountuid) {
		return executor.submit(() -> {
			//Try to get the account data from local. If it exists, return that.
			LAccountEntity localAccountProps = localRepo.getAccountProps(accountuid);
			if(localAccountProps != null)
				return new Gson().fromJson(localAccountProps.toJson(), GAccount.class);


			//If the account doesn't exist locally, try to get it from the server.
			try {
				SAccount serverAccountProps = serverRepo.getAccountProps(accountuid);
				return new Gson().fromJson(serverAccountProps.toJson(), GAccount.class);
			} catch (SocketTimeoutException e) {
				return null;
			}
		});
	}


	public ListenableFuture<Boolean> putAccount(@NonNull GAccount account) {
		return executor.submit(() -> {

			return true;
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
				return serverRepo.fileConn.getProps(fileuid).toJson();
			} catch (SocketTimeoutException e) {
				return null;
			}
		});
	}


	public ListenableFuture<InputStream> getFileContents(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			return localRepo.getFileContents(fileUID);
		});
	}



	public ListenableFuture<Boolean> putFileLocal(@NonNull LFileEntity file) {
		return executor.submit(() -> {
			localRepo.putFileProps(file);
			return true;
		});
	}

	public ListenableFuture<Boolean> putFileServer(@NonNull SFile file) {
		return executor.submit(() -> {
			serverRepo.putFileProps(file);
			return true;
		});
	}


	private enum location {	//May not want BOTH
		LOCAL, SERVER, NONE, BOTH
	}
	private void findClosestRepo(@NonNull UUID fileUID) {

	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	public ListenableFuture<Boolean> putBlockLocal(@NonNull LFileEntity file) {
		return executor.submit(() -> {
			//localRepo.putBlock(file);
			return true;
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


	public void copyFileToLocal(@NonNull UUID fileuid) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		OneTimeWorkRequest.Builder builder = domainAPI.buildWorker(fileuid, DomainAPI.Operation.COPY_TO_LOCAL);
		workManager.enqueue(builder.build());
	}
	public void copyFileToServer(@NonNull UUID fileuid) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		OneTimeWorkRequest.Builder builder = domainAPI.buildWorker(fileuid, DomainAPI.Operation.COPY_TO_SERVER);
		workManager.enqueue(builder.build());
	}

	protected ListenableFuture<Boolean> copyFileToLocalImmediate(@NonNull UUID fileuid) {
		return executor.submit(() -> domainAPI.copyFileToLocal(fileuid));
	}
	protected ListenableFuture<Boolean> copyFileToServerImmediate(@NonNull UUID fileuid) {
		return executor.submit(() -> domainAPI.copyFileToServer(fileuid));
	}


	public void removeFileFromLocal(@NonNull UUID fileuid) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		OneTimeWorkRequest.Builder builder = domainAPI.buildWorker(fileuid, DomainAPI.Operation.REMOVE_FROM_LOCAL);
		workManager.enqueue(builder.build());
	}
	public void removeFileFromServer(@NonNull UUID fileuid) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		OneTimeWorkRequest.Builder builder = domainAPI.buildWorker(fileuid, DomainAPI.Operation.REMOVE_FROM_SERVER);
		workManager.enqueue(builder.build());
	}
}



















