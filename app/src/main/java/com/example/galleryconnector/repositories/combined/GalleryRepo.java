package com.example.galleryconnector.repositories.combined;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import com.example.galleryconnector.repositories.combined.sync.SyncHandler;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.block.LBlock;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SBlock;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;
	private final SyncHandler syncHandler;

	private GFileUpdateObservers observers;



	public static GalleryRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GalleryRepo INSTANCE = new GalleryRepo();
	}
	private GalleryRepo() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
		syncHandler = SyncHandler.getInstance();

		observers = new GFileUpdateObservers();
	}

	public void initializeSyncing() {
		syncHandler.catchUpOnSyncing();
		observers.attachListeners(localRepo, serverRepo);
	}

	//---------------------------------------------------------------------------------------------

	public void addObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(GFileUpdateObservers.GFileObservable observer) {
		observers.removeObserver(observer);
	}

	public void notifyObservers(GFile file) {
		observers.notifyObservers(file);
	}

	//TODO Use this with DomainAPI and SyncHandler's doSomething() methods
	public boolean doesDeviceHaveInternet() {
		ConnectivityManager connectivityManager = (ConnectivityManager)
				MyApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

		Network nw = connectivityManager.getActiveNetwork();
		if(nw == null) return false;

		NetworkCapabilities cap = connectivityManager.getNetworkCapabilities(nw);
		return cap != null && (
				cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
				cap.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) );
	}

	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	@Nullable
	public GAccount getAccountProps(@NonNull UUID accountuid) throws FileNotFoundException, ConnectException {
		//Try to get the account data from local. If it exists, return that.
		try {
			LAccount local = localRepo.getAccountProps(accountuid);
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
	}


	public boolean putAccountPropsLocal(@NonNull GAccount gAccount) {
		LAccount account = new Gson().fromJson(gAccount.toJson(), LAccount.class);
		localRepo.putAccountProps(account);
		return true;
	}

	public boolean putAccountPropsServer(@NonNull GAccount gAccount) throws ConnectException {
		SAccount account = new Gson().fromJson(gAccount.toJson(), SAccount.class);
		serverRepo.putAccountProps(account);
		return true;
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	@Nullable
	public GFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		//Try to get the file data from local. If it exists, return that.
		try {
			LFile local = localRepo.getFileProps(fileUID);
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
	}


	//TODO Should we grab blocks from local if possible, and server if not?
	// That might just overcomplicate things since blocks for a file are probably all together anyway.
	public InputStream getFileContents(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		//Try to get the file data from local. If it exists, return that.
		try {
			return localRepo.getFileContents(fileUID);
		}
		catch (FileNotFoundException e) {
			//Do nothing
		} catch (DataNotFoundException e) {
			Log.e(TAG, "Local blockset is missing data!");
			throw new RuntimeException(e);
		}

		//If the file doesn't exist locally, try to get it from the server.
		try {
			return serverRepo.getFileContents(fileUID);
		} catch (FileNotFoundException e) {
			//Do nothing
		} catch (DataNotFoundException e) {
			Log.e(TAG, "Server blockset is missing data!");
			throw new RuntimeException(e);
		}

		//If the file doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("File not found with fileUID='%s'", fileUID));
	}


	//TODO Private these, and just give a "putFileProps" option that figures out where to put things and errors if out of date
	// Maybe not private them idk

	public GFile putFilePropsLocal(@NonNull GFile gFile) throws DataNotFoundException {
		return putFilePropsLocal(gFile, null, null);
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash) throws DataNotFoundException {
		LFile file = gFile.toLocalFile();
		try {
			LFile retFile = localRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromLocalFile(retFile);
		} catch (DataNotFoundException e) {
			throw new DataNotFoundException("Cannot put props, Local blockset is missing data!", e);
		}
	}

	public GFile putFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, DataNotFoundException, ConnectException {
		return putFilePropsServer(gFile, null, null);
	}
	public GFile putFilePropsServer(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws IllegalStateException, DataNotFoundException, ConnectException {
		SFile file = gFile.toServerFile();
		try {
			SFile retFile = serverRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromServerFile(retFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("PrevHashes do not match in putFileProps", e);
		} catch (DataNotFoundException e) {
			throw new DataNotFoundException("Cannot put props, Server blockset is missing data!", e);
		} catch (ConnectException e) {
			throw e;
		}
	}



	//DOES NOT UPDATE FILE PROPERTIES
	public GFile putDataLocal(@NonNull GFile file, @NonNull Uri source) throws UnknownHostException {
		try {
			LBlockHandler.BlockSet blockSet = localRepo.putData(source);

			//Update the given file properties with the new blockset data
			file.fileblocks = blockSet.blockList;
			file.filesize = blockSet.fileSize;
			file.filehash = blockSet.fileHash;

			file.changetime = Instant.now().getEpochSecond();
			file.modifytime = Instant.now().getEpochSecond();
			return file;
		} catch (UnknownHostException e) {
			System.out.println("BAD SOURCE BAD SOURCE	(or no internet idk)");
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	//DOES NOT UPDATE FILE PROPERTIES
	public GFile putDataServer(@NonNull GFile file, @NonNull Uri source) throws UnknownHostException, ConnectException {
		try {
			ServerRepo.BlockSet blockSet = serverRepo.putData(source);

			//Update the given file properties with the new blockset data
			file.fileblocks = blockSet.blockList;
			file.filesize = blockSet.fileSize;
			file.filehash = blockSet.fileHash;

			file.changetime = Instant.now().getEpochSecond();
			file.modifytime = Instant.now().getEpochSecond();

			return file;
		} catch (UnknownHostException e) {
			System.out.println("BAD SOURCE BAD SOURCE	(or no internet idk)");
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void deleteFilePropsLocal(@NonNull UUID fileUID) {
		localRepo.deleteFileProps(fileUID);
	}
	public void deleteFilePropsServer(@NonNull UUID fileUID) throws ConnectException {
		serverRepo.deleteFileProps(fileUID);
	}


	//TODO Considering caching the UUIDs of the files on server to help speed this up
	// Or at least for another similar method.

	public boolean isFileLocal(@NonNull UUID fileUID) {
		try {
			localRepo.getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public boolean isFileServer(@NonNull UUID fileUID) throws ConnectException {
		try {
			serverRepo.getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
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
	public GBlock getBlockProps(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		//Try to get the block data from local. If it exists, return that.
		try {
			LBlock local = localRepo.getBlockProps(blockHash);
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
	}


	public byte[] getBlockContents(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		//Try to get the block data from local. If it exists, return that.
		try {
			return localRepo.getBlockContents(blockHash);
		} catch (DataNotFoundException e) {
			//Do nothing
		}

		//If the block doesn't exist locally, try to get it from the server.
		try {
			return serverRepo.getBlockContents(blockHash);
		} catch (DataNotFoundException e) {
			//Do nothing
		}

		//If the block doesn't exist in either, throw an exception
		throw new FileNotFoundException(String.format("Block not found with blockHash='%s'", blockHash));
	}



	public boolean putBlockContentsLocal(@NonNull byte[] data) throws IOException {
		localRepo.putBlockData(data);
		return true;
	}

	public boolean putBlockContentsServer(@NonNull byte[] data) throws IOException {
		serverRepo.putBlockContents(data);
		return true;
	}



	public boolean isBlockLocal(@NonNull String blockHash) {
		try {
			localRepo.getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public boolean isBlockServer(@NonNull String blockHash) throws ConnectException {
		try {
			serverRepo.getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (ConnectException e) {
			throw e;
		}
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
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void copyFileToServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}


	public void removeFileFromLocal(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void removeFileFromServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}



















