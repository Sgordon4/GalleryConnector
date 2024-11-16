package com.example.galleryconnector.repositories.local;

import static com.example.galleryconnector.repositories.local.block.LBlockHandler.CHUNK_SIZE;

import android.content.ContentResolver;
import android.content.Context;
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
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

	public LAccountEntity getAccountProps(@NonNull UUID accountUID) {
		throw new RuntimeException("Stub!");
	}

	public void putAccountProps(@NonNull LAccountEntity accountProps) {
		throw new RuntimeException("Stub!");
	}




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



	public void putFileContents(@NonNull Uri source, @NonNull Context context) {
		Log.i(TAG, String.format("PUT FILE CONTENTS called with uri='%s'", source));

		//The destination system may already have some/all of the blocks in this uri.
		//We need to know what blocks in the blocklist local is missing.
		//To do that, we need the blocklist. Get the blocklist.
		BlockSet blockSet = getBlocksFromUri(source, context);


		//Write any blocks that are currently missing from the system
		ContentResolver contentResolver = context.getContentResolver();
		try (InputStream is = contentResolver.openInputStream(source)) {

			//For each block...
			for(int i = 0; i < blockSet.blockList.size(); i++) {
				if(getBlockProps( blockSet.blockList.get(i) ) != null) {	//If the block already exists
					is.skip(CHUNK_SIZE);                                	//Skip it
					continue;
				}

				//Read the block
				byte[] block = new byte[CHUNK_SIZE];
				int read = is.read(block);
				if(read == -1) continue;

				//Trim block if needed (for tail of the file, when not enough bytes to fill a full block)
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				//Write the block to the system
				putBlockContents(block);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		//Assuming all blocks have been successfully written, update the file info with the new blocks
		//TODO
		throw new RuntimeException("Stub!");
	}

	public void putFileContents(@NonNull String contents) {
		throw new RuntimeException("Stub!");
	}





	private class BlockSet {
		public List<String> blockList = new ArrayList<>();
		public int fileSize = 0;
		public String fileHash = "";
	}
	//Given a Uri, parse its contents into an evenly chunked set of blocks
	//Find the filesize and SHA-256 filehash while we do so.
	private BlockSet getBlocksFromUri(@NonNull Uri source, @NonNull Context context) {
		BlockSet blockSet = new BlockSet();

		try (InputStream is = context.getContentResolver().openInputStream(source);
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			//Read the next block
			byte[] block = new byte[CHUNK_SIZE];
			int read;
			while((read = dis.read(block)) != -1) {

				//Trim block if needed (for tail of the file, when not enough bytes to fill a full block)
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;
				blockSet.fileSize += block.length;

				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = BlockConnector.bytesToHex(hash);

				//Add to the hash list
				blockSet.blockList.add(hashString);
			}

			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		return blockSet;
	}

	//TODO Check that empty strings hash how we want them to
	//Given a String, parse its contents into a single block (This is really just for small Strings)
	//Find the filesize and SHA-256 filehash while we do so.
	private BlockSet getBlocksFromString(@NonNull String string) {
		BlockSet blockSet = new BlockSet();

		try {
			//Hash the block
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(string.getBytes());
			String hashString = BlockConnector.bytesToHex(hash);


			//There's only one hash in our HashList
			blockSet.blockList.add(hashString);

			//Get the size of the string
			blockSet.fileSize += string.length();

			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = hashString;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		return blockSet;
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

	public void putBlockContents(@NonNull byte[] contents) {
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
