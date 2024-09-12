package com.example.galleryconnector.repositories.local;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFileEntity;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;
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

		LFileEntity file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");

		return file;
	}


	public void putFile(@NonNull LFileEntity file) {
		Log.i(TAG, String.format("PUT FILE called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = getMissingBlocks(file.fileblocks);

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Get the previous file entry for sync purposes (can be null)
		LFileEntity prevFile = database.getFileDao().loadByUID(file.fileuid);

		//Now that we've confirmed all blocks exist, create/update the file metadata
		database.getFileDao().put(file);

		//Notify observers that there's been a change in file data
		observers.notifyObservers(file, prevFile);
	}
	public List<String> getMissingBlocks(List<String> blockset) {
		//Check if the blocks repo is missing any blocks from the blockset
		return blockset.stream()
				.filter(s -> blockHandler.getBlock(s) == null)
				.collect(Collectors.toList());
	}
}
