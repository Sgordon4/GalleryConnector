package com.example.galleryconnector.repositories.local.block;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class LBlockHandler {

	private static final String TAG = "Gal.LRepo.Block";
	private final String blockDir = "blocks";
	public final LBlockDao blockDao;

	//public static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB
	//public static final int CHUNK_SIZE = 4;  //4B (For testing)
	public static final int CHUNK_SIZE = 1024 * 1024;  //1MB (For testing)


	public LBlockHandler(LBlockDao blockDao) {
		this.blockDao = blockDao;
	}


	@Nullable
	public LBlockEntity getBlockProps(@NonNull String blockHash) throws FileNotFoundException {
		LBlockEntity block = blockDao.loadByHash(blockHash);
		if(block == null) throw new FileNotFoundException("Block not found! Hash: '"+blockHash+"'");
		return block;
	}

	@Nullable
	public Uri getBlockUri(@NonNull String blockHash) {
		File blockFile = getBlockLocationOnDisk(blockHash);
		//if(blockFile == null) throw new FileNotFoundException("Block contents do not exist! Hash='"+blockHash+"'");
		return Uri.fromFile(blockFile);
	}





	@Nullable
	public byte[] readBlock(@NonNull String blockHash) throws FileNotFoundException {

		Uri blockUri = getBlockUri(blockHash);
		File blockFile = new File(blockUri.getPath());

		if(!blockFile.exists())
			throw new FileNotFoundException("Block contents do not exist! Hash='"+blockHash+"'");

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
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		Log.i(TAG, String.format("Writing with blockHash='"+blockHash+"'"));


		//Get the location of the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);

		//If the block already exists, do nothing
		if(blockFile.exists() && blockFile.length() > 0)
			return blockHash;


		//Write the block data to the file
		if(!blockFile.exists())
			blockFile.createNewFile();
		try (FileOutputStream fos = new FileOutputStream(blockFile)) {
			fos.write(bytes);
		}


		//Create a new entry in the block table
		LBlockEntity blockEntity = new LBlockEntity(blockHash, bytes.length);
		blockDao.put(blockEntity);

		Log.i(TAG, "Uploading block complete");
		return blockHash;
	}

	public boolean deleteBlock(@NonNull String blockHash) {
		//Remove the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);
		return blockFile.delete();
	}


	public File getBlockLocationOnDisk(@NonNull String hash) {
		Context context = MyApplication.getAppContext();

		//Starting out of the app's data directory...
		String appDataDir = context.getApplicationInfo().dataDir;

		//Blocks are stored in a block subdirectory
		File blockRoot = new File(appDataDir, blockDir);
		if(!blockRoot.isDirectory())
			blockRoot.mkdir();

		//With each block named by its SHA256 hash. Don't create the blockFile here, handle that elsewhere.
		return new File(blockRoot, hash);
	}



	//---------------------------------------------------------------------------------------------


	public static class BlockSet {
		public List<String> blockList = new ArrayList<>();
		public int fileSize = 0;
		public String fileHash = "";
	}

	//Given a Uri, parse its contents into an evenly chunked set of blocks and write them to disk
	//Find the fileSize and SHA-256 fileHash while we do so.
	public BlockSet writeUriToBlocks(@NonNull Uri source) {
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


				//Write the block to the system
				String hashString = writeBlock(block);

				//Add to the blockSet
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



	//Given a single block, write to storage (mostly for testing use for small Strings)
	//Find the fileSize and SHA-256 fileHash while we do so.
	public BlockSet writeBytesToBlocks(@NonNull byte[] block) {
		BlockSet blockSet = new BlockSet();

		//Don't put empty blocks in the blocklist
		if(block.length == 0)
			return new BlockSet();

		try {
			//Write the block to the system
			String hashString = writeBlock(block);

			//Add to the blockSet
			blockSet.blockList.add(hashString);
			blockSet.fileSize = block.length;
			blockSet.fileHash = hashString;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return blockSet;
	}

}
