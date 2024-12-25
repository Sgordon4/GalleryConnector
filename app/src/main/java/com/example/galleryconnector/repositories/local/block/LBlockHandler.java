package com.example.galleryconnector.repositories.local.block;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class LBlockHandler {

	private static final String TAG = "Gal.LRepo.Block";
	private final String blockDir = "blocks";
	public final LBlockDao blockDao;

	//public static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB
	public static final int CHUNK_SIZE = 1024 * 1024;  //1MB (For testing)
	//public static final int CHUNK_SIZE = 4;  //4B (For testing)


	public LBlockHandler(LBlockDao blockDao) {
		this.blockDao = blockDao;
	}


	@NonNull
	public LBlock getBlockProps(@NonNull String blockHash) throws FileNotFoundException {
		LBlock block = blockDao.loadByHash(blockHash);
		if(block == null) throw new FileNotFoundException("Block not found! Hash: '"+blockHash+"'");
		return block;
	}

	@NonNull
	public Uri getBlockUri(@NonNull String blockHash) throws DataNotFoundException {
		File blockFile = getBlockLocationOnDisk(blockHash);
		if(!blockFile.exists())
			throw new DataNotFoundException("Block contents do not exist! Hash='"+blockHash+"'");
		return Uri.fromFile(blockFile);
	}





	@NonNull
	public byte[] readBlock(@NonNull String blockHash) throws DataNotFoundException {
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
			blockHash = BlockConnector.bytesToHex(hash);
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
		LBlock blockEntity = new LBlock(blockHash, bytes.length);
		blockDao.put(blockEntity);

		Log.v(TAG, "Uploading block complete. BlockHash: '"+blockHash+"'");
		return blockHash;
	}

	public boolean deleteBlock(@NonNull String blockHash) {
		//Remove the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);
		return blockFile.delete();
	}


	//WARNING: This method does not create the file or parent directory, it only provides the location
	@NonNull
	private File getBlockLocationOnDisk(@NonNull String hash) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Blocks are stored in a block subdirectory
		File blockRoot = new File(appDataDir, blockDir);

		//With each block named by its SHA256 hash. Don't create the blockFile here, handle that elsewhere.
		return new File(blockRoot, hash);
	}



	//---------------------------------------------------------------------------------------------


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
				block = dis.readNBytes(BlockConnector.CHUNK_SIZE);
				Log.d(TAG, "Read "+block.length);

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;


				//Write the block to the system
				String hashString = writeBlock(block);

				//Add to the blockSet
				blockSet.blockList.add(hashString);
				blockSet.fileSize += block.length;

			} while (block.length >= BlockConnector.CHUNK_SIZE);


			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
			Log.d(TAG, "File has "+blockSet.blockList.size()+" blocks, with a size of "+blockSet.fileSize+".");

		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {	//Should never happen
			throw new RuntimeException(e);
		}

		return blockSet;
	}



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

}
