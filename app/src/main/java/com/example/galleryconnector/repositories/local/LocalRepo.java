package com.example.galleryconnector.repositories.local;

import static com.example.galleryconnector.repositories.local.block.LBlockHandler.CHUNK_SIZE;

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
		Log.i(TAG, String.format("GET FILE PROPS called with fileUID='%s'", fileUID));

		LFileEntity file = database.getFileDao().loadByUID(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}


	public void putFileProps(@NonNull LFileEntity file) {
		Log.i(TAG, String.format("PUT FILE PROPS called with fileUID='%s'", file.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = file.fileblocks.stream()
				.filter(s -> blockHandler.getBlockProps(s) == null)
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
		BlockSet blockSet = writeUriToBlocks(source);

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
		BlockSet blockSet = writeStringToBlocks(contents);

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

	public LBlockEntity getBlockProps(@NonNull String blockHash) throws FileNotFoundException {
		Log.i(TAG, String.format("GET BLOCK PROPS called with blockHash='%s'", blockHash));

		LBlockEntity block = blockHandler.getBlockProps(blockHash);
		if(block == null) throw new FileNotFoundException("Block not found! Hash: '"+blockHash+"'");
		return block;
	}

	//TODO I don't think we want a putProps, since writeBlockContents takes care of that.
	// That's the only time a props object would be created.
	public void putBlockProps(@NonNull LBlockEntity blockEntity){
		Log.i(TAG, String.format("PUT BLOCK PROPS called with blockHash='%s'", blockEntity.blockhash));

		//If the underlying data doesn't exist, don't allow a properties write
		if(!blockHandler.getBlockExistsOnDisk(blockEntity.blockhash))
			throw new IllegalStateException("Block not found on disk! Hash: '"+blockEntity.blockhash+"'");

		//Now that we've confirmed things are good to go, create/update the block properties
		database.getBlockDao().put(blockEntity);
	}



	public Uri getBlockUri(@NonNull String blockHash) {
		throw new RuntimeException("Stub!");
	}

	/*	Don't use?
	public byte[] getBlockContents(@NonNull String blockHash) {
		throw new RuntimeException("Stub!");
	}
	 */


	//TODO Also don't think we are going to use this one, rather than the below writeUriToBlocks
	public void putBlockContents(@NonNull Uri source) {
		throw new RuntimeException("Stub!");
	}

	public void putBlockContents(@NonNull byte[] contents) {
		throw new RuntimeException("Stub!");
	}



	private class BlockSet {
		public List<String> blockList = new ArrayList<>();
		public int fileSize = 0;
		public String fileHash = "";
	}

	//Given a Uri, parse its contents into an evenly chunked set of blocks and write them to disk
	//Find the fileSize and SHA-256 fileHash while we do so.
	private BlockSet writeUriToBlocks(@NonNull Uri source) {
		BlockSet blockSet = new BlockSet();

		try (InputStream is = MyApplication.getAppContext().getContentResolver().openInputStream(source);
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

					if(block.length == 0)   //Don't put empty blocks in the blocklist
						continue;
				}


				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = BlockConnector.bytesToHex(hash);

				//If the block does not already exist, write the block to the system
				if(getBlockProps(hashString) == null) {
					putBlockContents(block);

					LBlockEntity blockObj = new LBlockEntity(hashString, block.length);
					putBlockProps(blockObj);
				}


				//Add to the hash list
				blockSet.blockList.add(hashString);

				blockSet.fileSize += block.length;
			}

			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );

		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		return blockSet;
	}


	//Given a String, parse its contents into a single block (mostly for testing use for small Strings)
	//Find the fileSize and SHA-256 fileHash while we do so.
	private BlockSet writeStringToBlocks(@NonNull String string) {
		BlockSet blockSet = new BlockSet();

		//Don't put empty blocks in the blocklist
		if(string.isEmpty())
			return new BlockSet();

		try {
			//Hash the block
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(string.getBytes());
			String hashString = BlockConnector.bytesToHex(hash);

			//If the block does not already exist, write the block to the system
			if(getBlockProps(hashString) == null) {
				putBlockContents(string.getBytes());

				LBlockEntity blockObj = new LBlockEntity(hashString, string.length());
				putBlockProps(blockObj);
			}


			//There's only one hash in our HashList
			blockSet.blockList.add(hashString);

			blockSet.fileSize = string.length();
			blockSet.fileHash = hashString;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		return blockSet;
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
