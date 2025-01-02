package com.example.galleryconnector.repositories.local;

import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ContentsNotFoundException;
import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.content.LContent;
import com.example.galleryconnector.repositories.local.content.LContentHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.local.sync.LSyncFile;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;



public class LocalRepo {
	private static final String TAG = "Gal.LRepo";
	public final LocalDatabase database;
	public final LContentHandler contentHandler;

	private final RoomDatabaseUpdateListener listener;

	public LocalRepo() {
		database = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );

		contentHandler = new LContentHandler(database.getContentDao());

		listener = new RoomDatabaseUpdateListener();
	}

	public static LocalRepo getInstance() {
		return LocalRepo.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final LocalRepo INSTANCE = new LocalRepo();
	}

	//---------------------------------------------------------------------------------------------


	public interface OnDataChangeListener<T> {
		void onDataChanged(T data);
	}

	//TODO We could probably do the account filtering here instead of GRepo, doesn't really matter
	public void setFileListener(int journalID, OnDataChangeListener<LJournal> onChanged) {
		Log.i(TAG, "Starting Local longpoll from journalID = "+journalID);
		LiveData<List<LJournal>> liveData = database.getJournalDao().longpollAfterID(journalID);
		listener.stopAll();
		listener.listen(liveData, journals -> {
			Log.i(TAG, journals.size()+" new Journals received in listener!");
			//System.out.println("New Journals recieved: ");
			for(LJournal journal : journals) {
				//System.out.println(journal);
				onChanged.onDataChanged(journal);
			}
		});
	}
	public void removeFileListener() {
		listener.stopAll();
	}


	/*

	File:
	getFileProps
	putFileProps
	getFileContents
	putFileContents
	deleteFile

	//Should anything outside of GalleryRepo know about blocks? No, right?
	//May as well have the option. Things like DomainAPI need it
	Block:
	private getBlockProps
	private putBlockProps
	private getBlockUrl
	private putBlockContents
	private getBlockContents
	private deleteBlock

	Account:
	getAccountProps
	putAccountProps

	Journal:
	getJournalEntriesAfter
	longpollJournalEntriesAfter
	getJournalEntriesForFile
	longpollJournalEntriesForFile

	 */



	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	public LAccount getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET LOCAL ACCOUNT PROPS called with accountUID='%s'", accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LAccount account = database.getAccountDao().loadByUID(accountUID);
		if(account == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return account;
	}

	public void putAccountProps(@NonNull LAccount accountProps) {
		Log.i(TAG, String.format("PUT LOCAL ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getAccountDao().put(accountProps);
	}




	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public LFile getFileProps(UUID fileUID) throws FileNotFoundException {
		Log.v(TAG, String.format("GET LOCAL FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LFile file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}


	public LFile putFileProps(@NonNull LFile fileProps, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws ContentsNotFoundException, IllegalStateException {
		Log.i(TAG, String.format("PUT LOCAL FILE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		//Check if the repo is missing the file contents. If so, we can't commit the file changes
		if(fileProps.filehash != null) {
			try {
				contentHandler.getProps(fileProps.filehash);
			} catch (ContentsNotFoundException e) {
				throw new ContentsNotFoundException("Cannot put props, system is missing file contents!");
			}
		}


		//Make sure the hashes match if any were passed
		LFile oldFile = database.getFileDao().loadByUID(fileProps.fileuid);
		if(oldFile != null) {
			if(prevFileHash != null && !Objects.equals(oldFile.filehash, prevFileHash))
				throw new IllegalStateException(String.format("File contents hash doesn't match for fileUID='%s'", oldFile.fileuid));
			if(prevAttrHash != null && !Objects.equals(oldFile.attrhash, prevAttrHash))
				throw new IllegalStateException(String.format("File attributes hash doesn't match for fileUID='%s'", oldFile.fileuid));
		}


		//Now that we've confirmed the contents exist, create/update the file metadata

		//Hash the user attributes
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(fileProps.userattr.toString().getBytes());
			fileProps.attrhash = bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		//Create/update the file
		database.getFileDao().put(fileProps);
		return fileProps;
	}
	//https://stackoverflow.com/a/9855338
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	private static String bytesToHex(@NonNull byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}


	//TODO When we get other infrastructure set up, just set delete prop
	public void deleteFileProps(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("DELETE LOCAL FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getFileDao().delete(fileUID);
	}



	/*
	public InputStream getFileContents(UUID fileUID) throws FileNotFoundException, ContentsNotFoundException {
		Log.i(TAG, String.format("GET LOCAL FILE CONTENTS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LFile file = getFileProps(fileUID);
		List<String> blockList = file.fileblocks;

		ContentResolver contentResolver = MyApplication.getAppContext().getContentResolver();
		List<InputStream> blockStreams = new ArrayList<>();
		for(String block : blockList) {
			Uri blockUri = getBlockContentsUri(block);
			blockStreams.add(contentResolver.openInputStream( Objects.requireNonNull( blockUri) ));
		}

		return new ConcatenatedInputStream(blockStreams);
	}
	 */



	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------

	@Nullable
	public Uri getContentUri(@NonNull String name) throws ContentsNotFoundException {
		Log.v(TAG, String.format("\nGET LOCAL CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Throws a ContentsNotFound exception if the content properties don't exist
		contentHandler.getProps(name);

		//Now that we know the properties exist, return the content uri
		return contentHandler.getContentUri(name);
	}


	//Helper method
	public LContent writeContents(@NonNull String name, @NonNull Uri source) throws FileNotFoundException {
		Log.v(TAG, String.format("\nGET LOCAL CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return contentHandler.writeContents(name, source);
	}


	public void deleteContents(@NonNull String name) {
		Log.i(TAG, String.format("\nDELETE LOCAL CONTENTS called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Remove the database entry first to avoid race conditions
		contentHandler.deleteProps(name);

		//Now remove the content itself from disk
		contentHandler.deleteContents(name);
	}



	//We don't actually need things to be able to access the content table

	/*
	public LContent getBlockProps(@NonNull String blockHash) throws ContentsNotFoundException {
		Log.v(TAG, String.format("GET LOCAL BLOCK PROPS called with blockHash='%s'", blockHash));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.getProps(blockHash);
	}
	public boolean getBlockPropsExist(@NonNull String blockHash) {
		try {
			getBlockProps(blockHash);
			return true;
		} catch (ContentsNotFoundException e) {
			return false;
		}
	}

	 */


	/*
	@Nullable	//Mostly used internally
	public byte[] getBlockContents(@NonNull String blockHash) throws ContentsNotFoundException {
		Log.i(TAG, String.format("\nGET LOCAL BLOCK CONTENTS called with blockHash='"+blockHash+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.readBlock(blockHash);
	}

	//Note: For efficiency, check if the block already exists before using this
	public LContentHandler.BlockSet putBlockData(@NonNull byte[] contents) throws IOException {
		Log.i(TAG, "\nPUT LOCAL BLOCK CONTENTS BYTE called");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.writeBytesToBlocks(contents);
	}

	public LContentHandler.BlockSet putBlockData(@NonNull Uri uri) throws IOException {
		Log.i(TAG, "\nPUT LOCAL BLOCK CONTENTS URI called");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.writeUriToBlocks(uri);
	}

	 */



	//---------------------------------------------------------------------------------------------
	// Last Sync
	//---------------------------------------------------------------------------------------------

	public LFile getLastSyncedData(@NonNull UUID fileUID) {
		return database.getSyncDao().loadByUID(fileUID);
	}

	public void putLastSyncedData(@NonNull LFile file) {
		database.getSyncDao().put(new LSyncFile(file));
	}

	public void deleteLastSyncedData(@NonNull UUID fileUID) {
		database.getSyncDao().delete(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	@NonNull
	public List<LJournal> getJournalEntriesAfter(int journalID) {
		Log.i(TAG, String.format("GET LOCAL JOURNALS AFTER ID called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		List<LJournal> journals = database.getJournalDao().loadAllAfterID(journalID);
		return journals != null ? journals : new ArrayList<>();
	}

	public List<LJournal> longpollJournalEntriesAfter(int journalID) {
		throw new RuntimeException("Stub!");
	}


	@NonNull
	public List<LJournal> getJournalEntriesForFile(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("GET LOCAL JOURNALS FOR FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		List<LJournal> journals = database.getJournalDao().loadAllByFileUID(fileUID);
		return journals != null ? journals : new ArrayList<>();
	}

	public List<LJournal> longpollJournalEntriesForFile(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub!");
	}



	//---------------------------------------------------------------------------------------------
	// Revise these
	//---------------------------------------------------------------------------------------------


	//I haven't found a great way to do this with livedata or InvalidationTracker yet
	public List<Pair<Long, LFile>> longpoll(int journalID) {
		//Try to get new data from the journal 6 times
		int tries = 6;
		do {
			List<Pair<Long, LFile>> data = longpollHelper(journalID);
			if(!data.isEmpty()) return data;

		} while(tries-- > 0);

		return new ArrayList<>();
	}


	private List<Pair<Long, LFile>> longpollHelper(int journalID) {
		//Get all recent journals after the given journalID
		List<LJournal> recentJournals = database.getJournalDao().loadAllAfterID(journalID);


		//We want all distinct fileUIDs with their greatest journalID. Journals come in sorted order.
		Map<UUID, LJournal> tempJournalMap = new HashMap<>();
		for(LJournal journal : recentJournals)
			tempJournalMap.put(journal.fileuid, journal);


		//Now grab each fileUID and get the file data
		List<LFile> files = database.getFileDao().loadByUID(tempJournalMap.keySet().toArray(new UUID[0]));


		//Combine the journalID with the file data and sort it by journalID
		List<Pair<Long, LFile>> journalFileList = files.stream().map(f -> {
			long journalIDforFile = tempJournalMap.get(f.fileuid).journalid;
			return new Pair<>(journalIDforFile, f);
		}).sorted(Comparator.comparing(longLFileEntityPair -> longLFileEntityPair.first)).collect(Collectors.toList());


		return journalFileList;
	}



	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}


}
