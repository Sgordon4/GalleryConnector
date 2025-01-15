package com.example.galleryconnector.repositories.local.content;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

public class LContentHandler {

	private static final String TAG = "Gal.LRepo.Cont";
	private final String contentDir = "content";
	public final LContentDao contentDao;

	//public static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB
	public static final int CHUNK_SIZE = 1024 * 1024;  //1MB (For testing)
	//public static final int CHUNK_SIZE = 4;  //4B (For testing)


	public LContentHandler(LContentDao contentDao) {
		this.contentDao = contentDao;
	}




	//---------------------------------------------------------------------------------------------
	// Props
	//---------------------------------------------------------------------------------------------


	@NonNull
	public LContent getProps(@NonNull String name) throws ContentsNotFoundException {
		LContent props = contentDao.loadByHash(name);
		if(props == null) throw new ContentsNotFoundException(name);
		return props;
	}


	//TODO When we start using usecount, make sure this doesn't overwrite it
	public LContent putProps(@NonNull String name, int size) {
		LContent newProps = new LContent(name, size);
		contentDao.put(newProps);
		return newProps;
	}


	public void deleteProps(@NonNull String name) {
		contentDao.delete(name);
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------


	//WARNING: The file at the end of this uri may not exist
	@NonNull
	public Uri getContentUri(@NonNull String name) {
		File contents = getContentLocationOnDisk(name);
		return Uri.fromFile(contents);
	}


	public boolean deleteContents(@NonNull String name) {
		File contentFile = getContentLocationOnDisk(name);
		return contentFile.delete();
	}


	//WARNING: This method does not create the file or parent directory, it only provides the location
	@NonNull
	private File getContentLocationOnDisk(@NonNull String hash) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Content is stored in a content subdirectory
		File contentRoot = new File(appDataDir, contentDir);

		//With each content file named by its SHA256 hash
		return new File(contentRoot, hash);
	}


	//---------------------------------------------------------------------------------------------


	//Helper method
	//Returns the number of bytes written
	public LContent writeContents(@NonNull String name, @NonNull Uri source) {
		try {
			File destinationFile = getContentLocationOnDisk(name);

			//Make sure the file exists before we write to it
			if(!destinationFile.exists()) {
				Files.createDirectories(destinationFile.toPath().getParent());
				Files.createFile(destinationFile.toPath());
			}


			//Write the source data to the destination file
			try (InputStream in = new URL(source.toString()).openStream();
				 FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {

				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}
			}
			int filesize = (int) destinationFile.length();


			//Now that the data has been written, create a new entry in the content table
			LContent contentProps = putProps(name, filesize);

			Log.v(TAG, "Uploading content complete. Name: '"+name+"'");
			return contentProps;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	/*
	@NonNull
	public byte[] readBlock(@NonNull String blockHash) throws ContentsNotFoundException {
		Uri blockUri = getBlockUri(blockHash);
		File blockFile = new File(Objects.requireNonNull( blockUri.getPath() ));

		//Read the block data from the file
		try (FileInputStream fis = new FileInputStream(blockFile)) {
			byte[] bytes = new byte[(int) blockFile.length()];
			fis.read(bytes);
			return bytes;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//Returns hash of the written block
	public String writeBlock(@NonNull byte[] bytes) throws IOException {
		//Hash the block
		String blockHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			blockHash = ContentConnector.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }

		Log.i(TAG, String.format("Writing "+bytes.length+" bytes with blockHash='"+blockHash+"'"));


		//Get the location of the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);

		//If the block already exists, do nothing
		if(blockFile.exists() && blockFile.length() > 0)
			return blockHash;


		//Write the block data to the file
		if(!blockFile.exists()) {
			Files.createDirectories(blockFile.toPath().getParent());
			Files.createFile(blockFile.toPath());
		}
		try (FileOutputStream fos = new FileOutputStream(blockFile)) {
			fos.write(bytes);
		}


		//Create a new entry in the block table
		LContent blockEntity = new LContent(blockHash, bytes.length);
		blockDao.put(blockEntity);

		Log.v(TAG, "Uploading block complete. BlockHash: '"+blockHash+"'");
		return blockHash;
	}
	 */


	//---------------------------------------------------------------------------------------------

	/*

	public static class BlockSet {
		public List<String> blockList = new ArrayList<>();
		public int fileSize = 0;
		public String fileHash = "";
	}

	//Helper method
	//Given a Uri, parse its contents into an evenly chunked set of blocks and write them to disk
	//Find the fileSize and SHA-256 fileHash while we do so.
	public BlockSet writeUriToBlocks(@NonNull Uri source) throws IOException {
		BlockSet blockSet = new BlockSet();

		Log.d(TAG, "Inside writeUriToBlocks");

		try (InputStream is = new URL(source.toString()).openStream();
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			Log.d(TAG, "Stream open");


			byte[] block;
			do {
				Log.d(TAG, "Reading...");
				block = dis.readNBytes(ContentConnector.MIN_PART_SIZE);
				Log.d(TAG, "Read "+block.length);

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;


				//Write the block to the system
				String hashString = writeBlock(block);

				//Add to the blockSet
				blockSet.blockList.add(hashString);
				blockSet.fileSize += block.length;

			} while (block.length >= ContentConnector.MIN_PART_SIZE);


			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = ContentConnector.bytesToHex( dis.getMessageDigest().digest() );
			Log.d(TAG, "File has "+blockSet.blockList.size()+" blocks, with a size of "+blockSet.fileSize+".");

		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {	//Should never happen
			throw new RuntimeException(e);
		}

		return blockSet;
	}
	 */



	/*
	//Given a single block, write to storage (mostly for testing use for small Strings)
	//Find the fileSize and SHA-256 fileHash while we do so.
	public BlockSet writeBytesToBlocks(@NonNull byte[] block) throws IOException {
		BlockSet blockSet = new BlockSet();

		//Don't put empty blocks in the blocklist
		if(block.length == 0)
			return new BlockSet();

		//Write the block to the system
		String hashString = writeBlock(block);

		//Add to the blockSet
		blockSet.blockList.add(hashString);
		blockSet.fileSize = block.length;
		blockSet.fileHash = hashString;

		return blockSet;
	}
	 */

}
