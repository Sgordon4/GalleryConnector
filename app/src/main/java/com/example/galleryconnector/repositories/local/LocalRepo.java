package com.example.galleryconnector.repositories.local;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.local.journal.LJournalEntity;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


//TODO Eventually change most/all of the localRepo.blockHandler or whatever to just the LRepo method

public class LocalRepo {
	private static final String TAG = "Gal.LRepo";
	public final LocalDatabase database;
	public final LBlockHandler blockHandler;

	private final LocalFileObservers observers;

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

	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public LFileEntity getFile(UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET FILE called with fileUID='%s'", fileUID));

		try {
			LFileEntity file = database.getFileDao().loadByUID(fileUID).get();
			if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
			return file;
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<Integer, LFileEntity> longpoll(int journalID) {
		Map<Integer, LFileEntity> journalFileMap = new HashMap<>();
		try {
			List<LJournalEntity> recentJournals = database.getJournalDao().loadAllAfterID(journalID).get();


			for(LJournalEntity journal : recentJournals) {
				//Go through and indiscriminately add to map, fileuid : LJournalEntity
			}


		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}




	public void putFile(@NonNull LFileEntity file) {
		Log.i(TAG, String.format("PUT FILE called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = getMissingBlocks(file.fileblocks);

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Now that we've confirmed all blocks exist, create/update the file metadata ------

		//Hash the file attributes
		file.hashAttributes();

		//Create/update the file
		database.getFileDao().put(file);


		//TODO This won't work, we need the journalID. Need to set up a longpoll on local as well for listening goddamnit.
		//Notify observers that there's been a change in file data
		int journalID = 4;
		observers.notifyObservers(journalID, file);
	}
	public List<String> getMissingBlocks(List<String> blockset) {
		//Check if the blocks repo is missing any blocks from the blockset
		return blockset.stream()
				.filter(s -> blockHandler.getBlock(s) == null)
				.collect(Collectors.toList());
	}
}
