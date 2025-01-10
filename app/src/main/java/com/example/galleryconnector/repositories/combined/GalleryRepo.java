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
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;
	private final SyncHandler syncHandler;
	private final WriteStalling writeStalling;

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
		writeStalling = WriteStalling.getInstance();

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


	public void putAccountProps(@NonNull GAccount gAccount) {
		throw new RuntimeException("Stub!");
	}

	protected void putAccountPropsLocal(@NonNull GAccount gAccount) {
		LAccount account = new Gson().fromJson(gAccount.toJson(), LAccount.class);
		localRepo.putAccountProps(account);
	}

	protected void putAccountPropsServer(@NonNull GAccount gAccount) throws ConnectException {
		SAccount account = new Gson().fromJson(gAccount.toJson(), SAccount.class);
		serverRepo.putAccountProps(account);
	}


	//---------------------------------------------------------------------------------------------
	// File Props
	//---------------------------------------------------------------------------------------------



	public long requestWriteLock(UUID fileUID) {
		return writeStalling.requestWriteLock(fileUID);
	}
	public void releaseWriteLock(UUID fileUID, long lockStamp) {
		writeStalling.releaseWriteLock(fileUID, lockStamp);
	}


	//TODO This is the grossest method in this system. Figure out a way to split things up and make it better. Also move to writeStalling
	//Actually writes to a temp file, which needs to be persisted later
	//Optimistically assumes the file exists in one of the repos. If not, this temp file will be deleted when we try to persist.
	public String writeFile(UUID fileUID, byte[] contents, String lastHash, long lockStamp) throws IOException {
		if(!writeStalling.isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");

		return writeStalling.write(fileUID, contents, lastHash);
	}
	public void writeFile(UUID fileUID, Uri contents, String lastHash, long lockStamp) {
		throw new RuntimeException("Stub!");
	}


	//Persist a stall file to a repo (if the file already exists), merging if needed
	protected void persistStalledWrite(UUID fileUID, long lockStamp) {
		if(!writeStalling.isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");


		//If there is no data to persist, do nothing
		if(!writeStalling.doesStallFileExist(fileUID))
			return;

		String stallHash;
		String syncHash;
		try {
			stallHash = writeStalling.getStallFileAttribute(fileUID, "hash");
			assert stallHash != null;
			syncHash = writeStalling.getStallFileAttribute(fileUID, "synchash");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}


		//If the stall file has no updates since its last sync, everything should be up to date.
		boolean stallHasChanges = !Objects.equals(stallHash, syncHash);
		if(!stallHasChanges) {
			//It's likely that there haven't been any updates in the last 5 seconds, so now is a good time to delete the stall file
			writeStalling.delete(fileUID);
			return;
		}



		//If the stall file has updates, we need to decide if we need to do anything before we write
		GFile existingFileProps;
		try {
			existingFileProps = getFileProps(fileUID);
		} catch (ConnectException e) {
			//If the file is not in local and we can't connect to the server, we can't write anything. Skip for now
			return;
		} catch (FileNotFoundException e) {
			//If the file is not in local OR server then there's nowhere to write, and either the client wrote to this UUID as a mistake
			// or the file was just deleted. Either way, we can discard the data.
			writeStalling.delete(fileUID);
			return;
		}


		File stallFile = writeStalling.getStallFile(fileUID);

		//If the repository doesn't have any changes, we can write stall straight to repo
		boolean repoHasChanges = !Objects.equals(existingFileProps.filehash, syncHash);
		if(!repoHasChanges || existingFileProps.filehash == null) {
			//Find which repo to write to
			try {
				if(isFileLocal(fileUID)) {
					int fileSize = putContentsLocal(stallHash, Uri.fromFile(stallFile));

					existingFileProps.filehash = stallHash;
					existingFileProps.filesize = fileSize;
					existingFileProps.changetime = Instant.now().getEpochSecond();
					existingFileProps.modifytime = Instant.now().getEpochSecond();

					if(syncHash == null) syncHash = "null";
					putFilePropsLocal(existingFileProps, syncHash, existingFileProps.attrhash);
				}
				else {
					int fileSize = putContentsServer(stallHash, stallFile);

					existingFileProps.filehash = stallHash;
					existingFileProps.filesize = fileSize;
					existingFileProps.changetime = Instant.now().getEpochSecond();
					existingFileProps.modifytime = Instant.now().getEpochSecond();

					if(syncHash == null) syncHash = "null";
					putFilePropsServer(existingFileProps, syncHash, existingFileProps.attrhash);
				}
			} catch (FileNotFoundException | ContentsNotFoundException e) {
				throw new RuntimeException(e);
			}
			//If the file isn't local and we can't connect to the server, skip and try again later
			catch (ConnectException e) {
				return;
			}
		}
		//Otherwise, since both the repo and the stall file have changes, we need to merge before we can write
		else {
			try {
				Uri stallContents = Uri.fromFile(stallFile);
				Uri repoContents = getContentUri(existingFileProps.filehash);
				Uri syncContents = syncHash != null ? getContentUri(syncHash) : null;


				if(existingFileProps.isdir) {
					byte[] mergedContents = MergeUtilities.mergeDirectories(stallContents, repoContents, syncContents);
					writeStalling.write(fileUID, mergedContents, stallHash);
				}
				else if(existingFileProps.islink) {
					throw new RuntimeException("Stub!");
				}
				else {
					throw new RuntimeException("Stub!");
				}


			} //If the file isn't local and we can't connect to the server, skip and try again later
			catch (ConnectException e) {
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
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



















