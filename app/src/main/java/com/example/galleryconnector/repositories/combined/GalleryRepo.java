package com.example.galleryconnector.repositories.combined;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.GAccount;
import com.example.galleryconnector.repositories.combined.combinedtypes.GBlock;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.combined.movement.ImportExportWorker;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.account.LAccountEntity;
import com.example.galleryconnector.repositories.local.block.LBlockEntity;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SBlock;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.ConnectException;
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
			try {
				LAccountEntity local = localRepo.getAccountProps(accountuid);
				return new Gson().fromJson(local.toJson(), GAccount.class);
			}
			catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the account doesn't exist locally, try to get it from the server.
			try {
				SAccount server = serverRepo.getAccountProps(accountuid);
				return new Gson().fromJson(server.toJson(), GAccount.class);

				//TODO If the account exists on server but not on local, we may want to copy it to local.
				// Not always though, so it shouldn't be done *here*
				// TBH that can probably just be done by Putting props to both every time
			} catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the file doesn't exist in either, throw an exception
			throw new FileNotFoundException(String.format("Account not found with accountUID='%s'", accountuid));
		});
	}


	public ListenableFuture<Boolean> putAccountPropsLocal(@NonNull GAccount gAccount) {
		return executor.submit(() -> {
			LAccountEntity account = new Gson().fromJson(gAccount.toJson(), LAccountEntity.class);
			localRepo.putAccountProps(account);
			return true;
		});
	}

	public ListenableFuture<Boolean> putAccountPropsServer(@NonNull GAccount gAccount) {
		return executor.submit(() -> {
			SAccount account = new Gson().fromJson(gAccount.toJson(), SAccount.class);
			serverRepo.putAccountProps(account);
			return true;
		});
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	@Nullable
	public ListenableFuture<GFile> getFileProps(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			//Try to get the file data from local. If it exists, return that.
			try {
				LFileEntity local = localRepo.getFileProps(fileUID);
				return GFile.fromLocalFile(local);
			}
			catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the file doesn't exist locally, try to get it from the server.
			try {
				SFile server = serverRepo.getFileProps(fileUID);
				return GFile.fromServerFile(server);
			} catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the file doesn't exist in either, throw an exception
			throw new FileNotFoundException(String.format("File not found with fileUID='%s'", fileUID));
		});
	}


	//TODO Should we grab blocks from local if possible, and server if not?
	// That might just overcomplicate things since blocks for a file are probably all together anyway.
	public ListenableFuture<InputStream> getFileContents(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			//Try to get the file data from local. If it exists, return that.
			try {
				return localRepo.getFileContents(fileUID);
			}
			catch (FileNotFoundException e) {
				Log.i(TAG, "File not found locally, trying server");
				//Do nothing
			}

			//If the file doesn't exist locally, try to get it from the server.
			try {
				return serverRepo.getFileContents(fileUID);
			} catch (FileNotFoundException e) {
				//Do nothing
			} catch (ConnectException | SocketTimeoutException e) {
				//Do nothing
			}

			//If the file doesn't exist in either, throw an exception
			throw new FileNotFoundException(String.format("File not found with fileUID='%s'", fileUID));
		});
	}



	public ListenableFuture<Boolean> putFilePropsLocal(@NonNull GFile gFile) {
		return executor.submit(() -> {
			LFileEntity file = gFile.toLocalFile();
			localRepo.putFileProps(file);
			return true;
		});
	}

	public ListenableFuture<Boolean> putFilePropsServer(@NonNull GFile gFile) {
		return executor.submit(() -> {
			SFile file = gFile.toServerFile();

			try {
				serverRepo.putFileProps(file);
			} catch (ConnectException | SocketTimeoutException e) {
				Log.e(TAG, "TIMEOUT in putFileProps");
				return false;
			}
			return true;
		});
	}



	public ListenableFuture<Boolean> putFileContentsLocal(@NonNull UUID fileUID, @NonNull Uri source) {
		return executor.submit(() -> {
			localRepo.putFileContents(fileUID, source);
			return true;
		});
	}
	public ListenableFuture<Boolean> putFileContentsServer(@NonNull UUID fileUID, @NonNull Uri source) {
		return executor.submit(() -> {
			try {
				serverRepo.putFileContents(fileUID, source);
			} catch (ConnectException | SocketTimeoutException e) {
				Log.e(TAG, "TIMEOUT in putFileContents");
				return false;
			}
			return true;
		});
	}


	public ListenableFuture<Boolean> deleteFilePropsLocal(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			localRepo.deleteFileProps(fileUID);
			return true;
		});
	}
	public ListenableFuture<Boolean> deleteFilePropsServer(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			try {
				serverRepo.deleteFileProps(fileUID);
				return true;
			} catch (FileNotFoundException e) {
				return false;
			} catch (ConnectException | SocketTimeoutException e) {
				Log.e(TAG, "TIMEOUT in deleteFileProps");
				return false;
			}
		});
	}


	//TODO Considering caching the UUIDs of the files on server to help speed this up
	// Or at least for another similar method.

	public ListenableFuture<Boolean> isFileLocal(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			try {
				localRepo.getFileContents(fileUID);
				return true;
			}
			catch (FileNotFoundException e) {
				return false;
			}
		});
	}

	public ListenableFuture<Boolean> isFileServer(@NonNull UUID fileUID) {
		return executor.submit(() -> {
			try {
				serverRepo.getFileContents(fileUID);
				return true;
			}
			catch (FileNotFoundException e) {
				return false;
			} catch (ConnectException | SocketTimeoutException e) {
				Log.e(TAG, "TIMEOUT in isFileServer");
				return false;
			}
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

	@Nullable
	public ListenableFuture<GBlock> getBlockProps(@NonNull String blockHash) {
		return executor.submit(() -> {
			//Try to get the block data from local. If it exists, return that.
			try {
				LBlockEntity local = localRepo.getBlockProps(blockHash);
				return new Gson().fromJson(local.toJson(), GBlock.class);
			}
			catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the block doesn't exist locally, try to get it from the server.
			try {
				SBlock server = serverRepo.getBlockProps(blockHash);
				return new Gson().fromJson(server.toJson(), GBlock.class);
			} catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the file doesn't exist in either, throw an exception
			throw new FileNotFoundException(String.format("Block not found with blockHash='%s'", blockHash));
		});
	}


	public ListenableFuture<byte[]> getBlockContents(@NonNull String blockHash) {
		return executor.submit(() -> {
			//Try to get the block data from local. If it exists, return that.
			try {
				return localRepo.getBlockContents(blockHash);
			}
			catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the block doesn't exist locally, try to get it from the server.
			try {
				return serverRepo.getBlockContents(blockHash);
			} catch (FileNotFoundException e) {
				//Do nothing
			}

			//If the block doesn't exist in either, throw an exception
			throw new FileNotFoundException(String.format("Block not found with blockHash='%s'", blockHash));
		});
	}



	public ListenableFuture<Boolean> putBlockContentsLocal(@NonNull byte[] data) {
		return executor.submit(() -> {
			localRepo.putBlockContents(data);
			return true;
		});
	}

	public ListenableFuture<Boolean> putBlockContentsServer(@NonNull byte[] data) {
		return executor.submit(() -> {
			serverRepo.putBlockContents(data);
			return true;
		});
	}



	public ListenableFuture<Boolean> isBlockLocal(@NonNull String blockHash) {
		return executor.submit(() -> {
			try {
				localRepo.getBlockProps(blockHash);
				return true;
			}
			catch (FileNotFoundException e) {
				return false;
			}
		});
	}

	public ListenableFuture<Boolean> isBlockServer(@NonNull String blockHash) {
		return executor.submit(() -> {
			try {
				serverRepo.getBlockProps(blockHash);
				return true;
			}
			catch (FileNotFoundException e) {
				return false;
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



















