package com.example.galleryconnector.repositories.local;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.block.LBlock;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.local.sync.LSyncFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
	public final LBlockHandler blockHandler;

	private final RoomDatabaseUpdateListener listener;

	public LocalRepo() {
		database = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );

		blockHandler = new LBlockHandler(database.getBlockDao());

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


	public LFile putFileProps(@NonNull LFile file, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws DataNotFoundException, IllegalStateException {
		Log.i(TAG, String.format("PUT LOCAL FILE PROPS called with fileUID='%s'", file.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = file.fileblocks.stream()
				.filter( b -> !getBlockPropsExist(b) )
				.collect(Collectors.toList());

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new DataNotFoundException("Cannot put props, system is missing "+missingBlocks.size()+" blocks!");


		//Make sure the hashes match if any were passed
		LFile oldFile = database.getFileDao().loadByUID(file.fileuid);
		if(oldFile != null) {
			if(prevFileHash != null && !oldFile.filehash.equals(prevFileHash))
				throw new IllegalStateException(String.format("File contents hash doesn't match for fileUID='%s'", oldFile.fileuid));
			if(prevAttrHash != null && ( oldFile.attrhash == null || !oldFile.attrhash.equals(prevAttrHash) ))
				throw new IllegalStateException(String.format("File attributes hash doesn't match for fileUID='%s'", oldFile.fileuid));
		}


		//Now that we've confirmed all blocks exist, create/update the file metadata:

		//Hash the user attributes
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(file.userattr.toString().getBytes());
			file.attrhash = bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		//Create/update the file
		database.getFileDao().put(file);
		return file;
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



	public InputStream getFileContents(UUID fileUID) throws FileNotFoundException, DataNotFoundException {
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


	public LBlockHandler.BlockSet putData(@NonNull Uri source) throws IOException {
		Log.i(TAG, String.format("PUT LOCAL FILE CONTENTS (Uri) called with Uri='%s'", source));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Write the Uri to the system as a set of blocks
		LBlockHandler.BlockSet blockSet = blockHandler.writeUriToBlocks(source);

		Log.d(TAG, "Blocks written to system");

		return blockSet;
	}


	public LBlockHandler.BlockSet putData(@NonNull String contents) throws IOException {
		Log.i(TAG, String.format("PUT LOCAL FILE CONTENTS (String) called with size='%s'", contents.length()));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Write the String to the system as a set of blocks
		return blockHandler.writeBytesToBlocks(contents.getBytes());
	}


	public void deleteFileProps(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("DELETE LOCAL FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getFileDao().delete(fileUID);
	}



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
	// Block
	//---------------------------------------------------------------------------------------------

	public LBlock getBlockProps(@NonNull String blockHash) throws FileNotFoundException {
		Log.i(TAG, String.format("GET LOCAL BLOCK PROPS called with blockHash='%s'", blockHash));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.getBlockProps(blockHash);
	}
	public boolean getBlockPropsExist(@NonNull String blockHash) {
		try {
			getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}


	@Nullable
	public Uri getBlockContentsUri(@NonNull String blockHash) throws DataNotFoundException {
		Log.i(TAG, String.format("\nGET LOCAL BLOCK URI called with blockHash='"+blockHash+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.getBlockUri(blockHash);
	}



	@Nullable	//Mostly used internally
	public byte[] getBlockContents(@NonNull String blockHash) throws DataNotFoundException {
		Log.i(TAG, String.format("\nGET LOCAL BLOCK CONTENTS called with blockHash='"+blockHash+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.readBlock(blockHash);
	}

	public LBlockHandler.BlockSet putBlockData(@NonNull byte[] contents) throws IOException {
		Log.i(TAG, "\nPUT LOCAL BLOCK CONTENTS BYTE called");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.writeBytesToBlocks(contents);
	}

	public LBlockHandler.BlockSet putBlockData(@NonNull Uri uri) throws IOException {
		Log.i(TAG, "\nPUT LOCAL BLOCK CONTENTS URI called");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockHandler.writeUriToBlocks(uri);
	}



	public void deleteBlock(@NonNull String blockHash) {
		Log.i(TAG, String.format("\nDELETE LOCAL BLOCK called with blockHash='"+blockHash+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Remove the database entry first to avoid race conditions
		database.getBlockDao().delete(blockHash);

		//Now remove the block itself from disk
		blockHandler.deleteBlock(blockHash);
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
