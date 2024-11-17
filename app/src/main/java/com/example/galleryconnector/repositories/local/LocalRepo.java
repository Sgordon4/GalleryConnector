package com.example.galleryconnector.repositories.local;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.account.LAccountEntity;
import com.example.galleryconnector.repositories.local.block.LBlockEntity;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.local.journal.LJournalEntity;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


//TODO Eventually change most/all of the localRepo.blockHandler or whatever to just the LRepo method

public class LocalRepo {
	private static final String TAG = "Gal.LRepo";
	public final LocalDatabase database;
	public final LBlockHandler blockHandler;

	private final LocalFileObservers observers;
	private LiveData<List<LJournalEntity>> latestJournals;

	public LocalRepo() {
		database = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );

		blockHandler = new LBlockHandler(database.getBlockDao());

		observers = new LocalFileObservers();
	}

	public static LocalRepo getInstance() {
		return LocalRepo.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final LocalRepo INSTANCE = new LocalRepo();
	}

	//---------------------------------------------------------------------------------------------

	public void addObserver(LocalFileObservers.LFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(LocalFileObservers.LFileObservable observer) {
		observers.removeObserver(observer);
	}


	int nextIndex = 0;
	public void startListening(int journalID) {
		database.getJournalDao().longpollAfterID(journalID).observeForever(lJournalEntities -> {
			for(; nextIndex < lJournalEntities.size(); nextIndex++) {
				LJournalEntity journal = lJournalEntities.get(nextIndex);

				//Get the file that the journal is linked to
				LFileEntity file = database.getFileDao().loadByUID(journal.fileuid);
				if(file == null) throw new IllegalStateException("File not found! ID: '"+journal.fileuid+"'");

				//Send it off to the observers
				observers.notifyObservers(journal.journalid, file);

				nextIndex++;
			}
		});
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

	public LAccountEntity getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET ACCOUNT PROPS called with accountUID='%s'", accountUID));

		LAccountEntity account = database.getAccountDao().loadByUID(accountUID);
		if(account == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return account;
	}

	public void putAccountProps(@NonNull LAccountEntity accountProps) {
		Log.i(TAG, String.format("PUT ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));

		database.getAccountDao().put(accountProps);
	}




	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public LFileEntity getFileProps(UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET FILE PROPS called with fileUID='%s'", fileUID));

		LFileEntity file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}


	public void putFileProps(@NonNull LFileEntity file) {
		Log.i(TAG, String.format("PUT FILE PROPS called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = file.fileblocks.stream()
				.filter( b -> !getBlockPropsExist(b) )
				.collect(Collectors.toList());

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Now that we've confirmed all blocks exist, create/update the file metadata:

		//Hash the file attributes
		file.hashAttributes();

		//Create/update the file
		database.getFileDao().put(file);
	}



	public Uri getFileContents(UUID fileUID) {
		Log.i(TAG, String.format("GET FILE CONTENTS called with fileUID='%s'", fileUID));
		throw new RuntimeException("Stub!");
	}


	public void putFileContents(@NonNull UUID fileUID, @NonNull Uri source) throws FileNotFoundException {
		Log.i(TAG, String.format("PUT FILE CONTENTS (Uri) called with fileUID='%s'", fileUID));
		LFileEntity file = getFileProps(fileUID);

		//Write the Uri to the system as a set of blocks
		LBlockHandler.BlockSet blockSet = blockHandler.writeUriToBlocks(source);

		//Update the file properties with the new block information
		file.fileblocks = blockSet.blockList;
		file.filesize = blockSet.fileSize;
		file.filehash = blockSet.fileHash;

		//And update the file information
		putFileProps(file);
	}


	public void putFileContents(@NonNull UUID fileUID, @NonNull String contents) throws FileNotFoundException {
		Log.i(TAG, String.format("PUT FILE CONTENTS (String) called with fileUID='%s'", fileUID));
		LFileEntity file = getFileProps(fileUID);

		//Write the String to the system as a set of blocks
		LBlockHandler.BlockSet blockSet = blockHandler.writeBytesToBlocks(contents.getBytes());

		//Update the file properties with the new block information
		file.fileblocks = blockSet.blockList;
		file.filesize = blockSet.fileSize;
		file.filehash = blockSet.fileHash;

		//And update the file information
		putFileProps(file);
	}




	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	@Nullable
	public LBlockEntity getBlockProps(@NonNull String blockHash) {
		Log.i(TAG, String.format("GET BLOCK PROPS called with blockHash='%s'", blockHash));
		return blockHandler.getBlockProps(blockHash);
	}
	public boolean getBlockPropsExist(@NonNull String blockHash) {
		return getBlockProps(blockHash) != null;
	}


	@Nullable
	public Uri getBlockUri(@NonNull String blockHash) {
		Log.i(TAG, String.format("\nGET BLOCK URI called with blockHash='"+blockHash+"'"));
		return blockHandler.getBlockUri(blockHash);
	}



	@Nullable	//Mostly used internally
	public byte[] getBlockContents(@NonNull String blockHash) {
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




	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	public List<LJournalEntity> getJournalEntriesAfter(int journalID) {
		throw new RuntimeException("Stub!");
	}

	public List<LJournalEntity> longpollJournalEntriesAfter(int journalID) {
		throw new RuntimeException("Stub!");
	}


	public List<LJournalEntity> getJournalEntriesForFile(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub!");
	}

	public List<LJournalEntity> longpollJournalEntriesForFile(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub!");
	}



	//---------------------------------------------------------------------------------------------
	// Revise these
	//---------------------------------------------------------------------------------------------


	//I haven't found a great way to do this with livedata or InvalidationTracker yet
	public List<Pair<Long, LFileEntity>> longpoll(int journalID) {
		//Try to get new data from the journal 6 times
		int tries = 6;
		do {
			List<Pair<Long, LFileEntity>> data = longpollHelper(journalID);
			if(!data.isEmpty()) return data;

		} while(tries-- > 0);

		return new ArrayList<>();
	}


	private List<Pair<Long, LFileEntity>> longpollHelper(int journalID) {
		//Get all recent journals after the given journalID
		List<LJournalEntity> recentJournals = database.getJournalDao().loadAllAfterID(journalID);


		//We want all distinct fileUIDs with their greatest journalID. Journals come in sorted order.
		Map<UUID, LJournalEntity> tempJournalMap = new HashMap<>();
		for(LJournalEntity journal : recentJournals)
			tempJournalMap.put(journal.fileuid, journal);


		//Now grab each fileUID and get the file data
		List<LFileEntity> files = database.getFileDao().loadByUID(tempJournalMap.keySet().toArray(new UUID[0]));


		//Combine the journalID with the file data and sort it by journalID
		List<Pair<Long, LFileEntity>> journalFileList = files.stream().map(f -> {
			long journalIDforFile = tempJournalMap.get(f.fileuid).journalid;
			return new Pair<>(journalIDforFile, f);
		}).sorted(Comparator.comparing(longLFileEntityPair -> longLFileEntityPair.first)).collect(Collectors.toList());


		return journalFileList;
	}
}
