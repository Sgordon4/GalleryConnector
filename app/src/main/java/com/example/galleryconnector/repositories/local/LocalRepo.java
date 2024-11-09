package com.example.galleryconnector.repositories.local;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
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
	// File
	//---------------------------------------------------------------------------------------------


	public LFileEntity getFileProps(UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET FILE called with fileUID='%s'", fileUID));

		LFileEntity file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}

	public Uri getFileContents(UUID fileUID) {
		throw new RuntimeException("Stub!");
	}



	public void putFile(@NonNull LFileEntity file) {
		Log.i(TAG, String.format("PUT FILE called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = file.fileblocks.stream()
				.filter(s -> blockHandler.getBlockProps(s) == null)
				.collect(Collectors.toList());

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Now that we've confirmed all blocks exist, create/update the file metadata ------

		//Hash the file attributes
		file.hashAttributes();

		//Create/update the file
		database.getFileDao().put(file);
	}



	public void putFileContents(@NonNull Uri source) {
		throw new RuntimeException("Stub!");
	}

	public void putFileContents(@NonNull String contents) {
		throw new RuntimeException("Stub!");
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
	// Account
	//---------------------------------------------------------------------------------------------

	public LAccountEntity getAccountProps(@NonNull UUID accountUID) {
		throw new RuntimeException("Stub!");
	}

	public void putAccountProps(@NonNull LAccountEntity accountProps) {
		throw new RuntimeException("Stub!");
	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	public LBlockEntity getBlockProps(@NonNull String blockHash) {
		throw new RuntimeException("Stub!");
	}

	public void putBlockProps(@NonNull LBlockEntity blockEntity){
		throw new RuntimeException("Stub!");
	}


	public Uri getBlockUri(@NonNull String blockHash) {
		throw new RuntimeException("Stub!");
	}

	public byte[] getBlockContents(@NonNull String blockHash) {
		throw new RuntimeException("Stub!");
	}

	public void putBlockContents(@NonNull Uri source) {
		throw new RuntimeException("Stub!");
	}

	public void putBlockContents(@NonNull String contents) {
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
