package com.example.galleryconnector.repositories.combined;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.combinedtypes.GAccount;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.domain_movement.DomainAPI;
import com.example.galleryconnector.repositories.combined.sync.SyncHandler;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
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

	//TODO Use this with DomainAPI and SyncHandler's doSomething() methods. Also fix this up, doesn't work.
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
	// File Props
	//---------------------------------------------------------------------------------------------


	//Don't use these, imports need to be done separately
	/*
	public UUID createFile(byte[] contents) {
		throw new RuntimeException("Stub!");
	}
	public UUID createFile(Uri contents) {
		throw new RuntimeException("Stub!");
	}
	 */


	public long requestWriteLock(UUID fileUID) {
		return tempHelper.requestWriteLock(fileUID);
	}
	public void releaseWriteLock(UUID fileUID, long lockStamp) {
		tempHelper.releaseWriteLock(fileUID, lockStamp);
	}

	


	WriteStalling tempHelper = WriteStalling.getInstance();

	//Actually writes to a temp file, which needs to be persisted later
	//Optimistically assumes the file exists in one of the repos. If not, this temp file will be deleted later.
	public String writeFile(UUID fileUID, byte[] contents, String lastHash, long lockStamp) throws IOException {
		if(!tempHelper.isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");

		return tempHelper.write(fileUID, contents, lastHash);
	}
	public void writeFile(UUID fileUID, Uri contents, String lastHash, long lockStamp) {
		throw new RuntimeException("Stub!");
	}






	public Map<String, String> getFileAttributes(UUID fileUID) {
		throw new RuntimeException("Stub!");
	}



















	@NonNull
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



	//TODO Private these, and just give a "putFileProps" option that figures out where to put things and errors if out of date
	// Maybe not private them idk

	public GFile createFilePropsLocal(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsLocal(gFile, "null", "null");
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile) throws ContentsNotFoundException {
		return putFilePropsLocal(gFile, null, null);
	}
	public GFile putFilePropsLocal(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash) throws ContentsNotFoundException {
		LFile file = gFile.toLocalFile();
		try {
			LFile retFile = localRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromLocalFile(retFile);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, Local is missing content!", e);
		}
	}

	public GFile createFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsServer(gFile, "null", "null");
	}
	public GFile putFilePropsServer(@NonNull GFile gFile) throws IllegalStateException, ContentsNotFoundException, ConnectException {
		return putFilePropsServer(gFile, null, null);
	}
	public GFile putFilePropsServer(@NonNull GFile gFile, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws IllegalStateException, ContentsNotFoundException, ConnectException {
		SFile file = gFile.toServerFile();
		try {
			SFile retFile = serverRepo.putFileProps(file, prevFileHash, prevAttrHash);
			return GFile.fromServerFile(retFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("PrevHashes do not match in putFileProps", e);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, Server is missing content!", e);
		} catch (ConnectException e) {
			throw e;
		}
	}



	public void deleteFilePropsLocal(@NonNull UUID fileUID) {
		localRepo.deleteFileProps(fileUID);
	}
	public void deleteFilePropsServer(@NonNull UUID fileUID) throws ConnectException {
		serverRepo.deleteFileProps(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// File Contents
	//---------------------------------------------------------------------------------------------


	public Uri getContentUri(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		//Try to get the file contents from local. If they exist, return that.
		try {
			return localRepo.getContentUri(name);
		}
		catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the contents don't exist locally, try to get it from the server.
		try {
			return serverRepo.getContentDownloadUri(name);
		} catch (ContentsNotFoundException e) {
			//Do nothing
		}

		//If the contents don't exist in either, throw an exception
		throw new ContentsNotFoundException(String.format("Contents not found with name='%s'", name));
	}


	//Note for future me, UnknownHostException can be thrown for web sources, not in this method though
	//System.out.println("BAD SOURCE BAD SOURCE	(or no internet idk)");

	//Helper method
	//WARNING: DOES NOT UPDATE FILE PROPERTIES ON LOCAL
	public int putContentsLocal(@NonNull String name, @NonNull Uri source) throws FileNotFoundException {
		return localRepo.writeContents(name, source).size;
	}
	//Helper method
	//WARNING: DOES NOT UPDATE FILE PROPERTIES ON SERVER
	//WARNING: Source file must be on-disk
	public int putContentsServer(@NonNull String name, @NonNull File source) throws FileNotFoundException {
		return serverRepo.uploadData(name, source).size;
	}


	private String calculateFileHash(@NonNull File file) {
		try (FileInputStream fis = new FileInputStream(file.getPath());
			 DigestInputStream dis = new DigestInputStream(fis, MessageDigest.getInstance("SHA-256"))) {

			byte[] buffer = new byte[8192];
			while (dis.read(buffer) != -1) {
				//Reading through the file updates the digest
			}

			return ContentConnector.bytesToHex( dis.getMessageDigest().digest() );

		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Import/Export
	//---------------------------------------------------------------------------------------------


	/*

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
	 */


	//---------------------------------------------------------------------------------------------
	// Domain Movements
	//---------------------------------------------------------------------------------------------


	public void queueCopyFileToLocal(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void queueCopyFileToServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.COPY_TO_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}


	public void queueRemoveFileFromLocal(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_LOCAL);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void queueRemoveFileFromServer(@NonNull UUID fileuid) {
		try {
			domainAPI.enqueue(fileuid, DomainAPI.REMOVE_FROM_SERVER);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}



















