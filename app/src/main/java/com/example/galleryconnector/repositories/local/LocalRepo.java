package com.example.galleryconnector.repositories.local;

import static com.example.galleryconnector.repositories.server.connectors.BlockConnector.CHUNK_SIZE;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.block.LBlockEntity;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

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

	//Local observers could possibly be moved to GRepo
	public void addObserver(LocalFileObservers.LFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(LocalFileObservers.LFileObservable observer) {
		observers.removeObserver(observer);
	}

	//---------------------------------------------------------------------------------------------

	public LFileEntity uploadFile(@NonNull LFileEntity file, @NonNull Uri source,
								  @NonNull Context context) throws IOException {
		Log.i(TAG, String.format("UPLOAD FILE called with fileUID='%s'", file.fileuid));

		//Upload the blockset for the file. This does nothing to the block db if all blocks already exist.
		//This method updates fileblocks, filehash, and filesize for the passed file object
		file = uploadBlockset(file, source, context);


		//Now that the blockset is uploaded, create/update the file metadata
		database.getFileDao().put(file);


		//TODO I'm not sure if there's a way for room to do this after an trigger insert to Journal instead
		observers.notifyObservers(file);

		return file;
	}


	//Updates blockset, filehash, and filesize for the passed file object
	public LFileEntity uploadBlockset(@NonNull LFileEntity file, @NonNull Uri source,
									  @NonNull Context context) throws IOException {
		Log.i(TAG, String.format("UPLOAD BLOCKSET called with uri='%s'", source));
		ContentResolver contentResolver = context.getContentResolver();

		//We need to know what blocks in the blocklist the server is missing.
		//To do that, we need the blocklist. Get the blocklist.
		List<String> fileHashes = new ArrayList<>();
		//Find the filesize and SHA-256 filehash while we do so.
		int filesize = 0;
		String filehash;
		try (InputStream is = contentResolver.openInputStream(source);
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			//Read the next block
			byte[] block = new byte[CHUNK_SIZE];
			int read;
			while((read = dis.read(block)) != -1) {
				//Trim block if needed (tail of the file, not enough bytes to fill a full block)
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;
				filesize += block.length;

				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = BlockConnector.bytesToHex(hash);

				//Add to the hash list
				fileHashes.add(hashString);
			}

			filehash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		Log.d(TAG, "FileHashes: "+fileHashes);


		//Now try to upload/commit blocks
		commitBlocks(fileHashes, source, context);
		Log.d(TAG, "Successful blockset upload!");


		//Update the file info
		file.fileblocks = fileHashes;
		file.filehash = filehash;
		file.filesize = filesize;
		return file;
	}



	private void commitBlocks(@NonNull List<String> fileHashes, @NonNull Uri source, @NonNull Context context) throws IOException {
		ContentResolver contentResolver = context.getContentResolver();

		List<String> missingBlocks;
		do {
			//Get the list of missing blocks
			missingBlocks = getMissingBlocks(fileHashes);

			//For each missing block (if any)...
			for(String missingBlockHash : missingBlocks) {

				//Go to the correct position in the file
				int index = fileHashes.indexOf(missingBlockHash);
				int blockStart = index * CHUNK_SIZE;

				Log.d(TAG, String.format("BSUpload: Reading block at %s = '%s'", blockStart, missingBlockHash));
				try (InputStream is = contentResolver.openInputStream(source)) {
					//Read the missing block
					is.skip(blockStart);
					byte[] block = new byte[CHUNK_SIZE];
					int read = is.read(block);

					//Trim block if needed (tail of the file, not enough bytes to fill a full block)
					if (read != CHUNK_SIZE) {
						byte[] smallerData = new byte[read];
						System.arraycopy(block, 0, smallerData, 0, read);
						block = smallerData;
					}


					//TODO Actually save the block to disk
					// I'm also thinking about making some local connectors for this type of thing

					//Add it to the database
					LBlockEntity newBlock = new LBlockEntity(missingBlockHash, block.length);
					database.getBlockDao().put(newBlock);
				}
			}
		} while (!missingBlocks.isEmpty());
	}


	public List<String> getMissingBlocks(List<String> blocks) {
		List<LBlockEntity> existingBlocks = database.getBlockDao().loadAllByHash(blocks);

		for(LBlockEntity block : existingBlocks) {
			blocks.remove(block.blockhash);
		}
		return blocks;
	}


}
