package com.example.galleryconnector.repositories.local;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.block.LBlock;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		LiveData<List<LJournal>> liveData = database.getJournalDao().longpollAfterID(journalID);
		listener.stopAll();
		listener.listen(liveData, journals -> {
			System.out.println("New Journals recieved: ");
			for(LJournal journal : journals) {
				System.out.println(journal);
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
		Log.i(TAG, String.format("GET ACCOUNT PROPS called with accountUID='%s'", accountUID));

		LAccount account = database.getAccountDao().loadByUID(accountUID);
		if(account == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return account;
	}

	public void putAccountProps(@NonNull LAccount accountProps) {
		Log.i(TAG, String.format("PUT ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));

		database.getAccountDao().put(accountProps);
	}




	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public LFile getFileProps(UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET FILE PROPS called with fileUID='%s'", fileUID));

		LFile file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}


	public LFile putFileProps(@NonNull LFile file, @Nullable String prevFileHash, @Nullable String prevAttrHash) {
		Log.i(TAG, String.format("PUT FILE PROPS called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = file.fileblocks.stream()
				.filter( b -> !getBlockPropsExist(b) )
				.collect(Collectors.toList());

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Make sure the hashes match if any were passed
		LFile oldFile = database.getFileDao().loadByUID(file.fileuid);

		if(oldFile != null) {
			if(prevFileHash != null && !oldFile.filehash.equals(prevFileHash))
				throw new IllegalStateException(String.format("File contents hash doesn't match for fileUID='%s'", oldFile.fileuid));
			if(prevAttrHash != null && !oldFile.attrhash.equals(prevAttrHash))
				throw new IllegalStateException(String.format("File attributes hash doesn't match for fileUID='%s'", oldFile.fileuid));
		}


		//Now that we've confirmed all blocks exist, create/update the file metadata:

		//Hash the file attributes
		file.hashAttributes();

		//Create/update the file
		database.getFileDao().put(file);
		return file;
	}



	public InputStream getFileContents(UUID fileUID) throws IOException {
		Log.i(TAG, String.format("GET FILE CONTENTS called with fileUID='%s'", fileUID));

		LFile file = getFileProps(fileUID);
		List<String> blockList = file.fileblocks;

		ContentResolver contentResolver = MyApplication.getAppContext().getContentResolver();
		List<InputStream> blockStreams = new ArrayList<>();
		for(String block : blockList) {
			Uri blockUri = getBlockContentsUri(block);
			blockStreams.add(contentResolver.openInputStream(blockUri)); //TODO Might be null if block doesn't exist
		}

		return new ConcatenatedInputStream(blockStreams);
	}


	public LFile putFileContents(@NonNull UUID fileUID, @NonNull Uri source) throws FileNotFoundException {
		Log.i(TAG, String.format("PUT FILE CONTENTS (Uri) called with fileUID='%s'", fileUID));
		LFile file = getFileProps(fileUID);

		System.out.println("\n\n\n\n");
		System.out.println("Local URL is "+source);

		//Write the Uri to the system as a set of blocks
		LBlockHandler.BlockSet blockSet = blockHandler.writeUriToBlocks(source);

		//Update the file properties with the new block information
		file.fileblocks = blockSet.blockList;
		file.filesize = blockSet.fileSize;
		file.filehash = blockSet.fileHash;

		//And update the file information
		file = putFileProps(file);
		return file;
	}


	public LFile putFileContents(@NonNull UUID fileUID, @NonNull String contents) throws FileNotFoundException {
		Log.i(TAG, String.format("PUT FILE CONTENTS (String) called with fileUID='%s'", fileUID));
		LFile file = getFileProps(fileUID);

		//Write the String to the system as a set of blocks
		LBlockHandler.BlockSet blockSet = blockHandler.writeBytesToBlocks(contents.getBytes());

		//Update the file properties with the new block information
		file.fileblocks = blockSet.blockList;
		file.filesize = blockSet.fileSize;
		file.filehash = blockSet.fileHash;

		//And update the file information
		putFileProps(file);
		return file;
	}


	public void deleteFileProps(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("DELETE FILE called with fileUID='%s'", fileUID));
		database.getFileDao().delete(fileUID);
	}




	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	@Nullable
	public LBlock getBlockProps(@NonNull String blockHash) throws FileNotFoundException {
		Log.i(TAG, String.format("GET BLOCK PROPS called with blockHash='%s'", blockHash));
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
	public Uri getBlockContentsUri(@NonNull String blockHash) {
		Log.i(TAG, String.format("\nGET BLOCK URI called with blockHash='"+blockHash+"'"));
		return blockHandler.getBlockUri(blockHash);
	}



	@Nullable	//Mostly used internally
	public byte[] getBlockContents(@NonNull String blockHash) throws FileNotFoundException {
		Log.i(TAG, String.format("\nGET BLOCK CONTENTS called with blockHash='"+blockHash+"'"));
		return blockHandler.readBlock(blockHash);
	}

	public LBlockHandler.BlockSet putBlockContents(@NonNull byte[] contents) {
		Log.i(TAG, "\nPUT BLOCK CONTENTS BYTE called");
		return blockHandler.writeBytesToBlocks(contents);
	}

	public LBlockHandler.BlockSet putBlockContents(@NonNull Uri uri) {
		Log.i(TAG, "\nPUT BLOCK CONTENTS URI called");
		return blockHandler.writeUriToBlocks(uri);
	}



	public void deleteBlock(@NonNull String blockHash) {
		Log.i(TAG, String.format("\nDELETE BLOCK called with blockHash='"+blockHash+"'"));

		//Remove the database entry first to avoid race conditions
		database.getBlockDao().delete(blockHash);

		//Now remove the block itself from disk
		blockHandler.deleteBlock(blockHash);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	public List<LJournal> getJournalEntriesAfter(int journalID) {
		Log.i(TAG, String.format("GET JOURNALS AFTER ID called with journalID='%s'", journalID));

		List<LJournal> journals = database.getJournalDao().loadAllAfterID(journalID);
		return journals != null ? journals : new ArrayList<>();
	}

	public List<LJournal> longpollJournalEntriesAfter(int journalID) {



		throw new RuntimeException("Stub!");
	}


	public List<LJournal> getJournalEntriesForFile(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("GET JOURNALS FOR FILE called with fileUID='%s'", fileUID));

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
}
