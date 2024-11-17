package com.example.galleryconnector.repositories.local.block;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class LBlockHandler {

	private static final String TAG = "Gal.LRepo.Block";
	private final String blockDir = "blocks";
	public final LBlockDao blockDao;

	public static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB
	

	public LBlockHandler(LBlockDao blockDao) {
		this.blockDao = blockDao;
	}


	//TODO Make sure this returns null if not exist, or maybe throw exception idk. Prob just null.
	@Nullable
	public LBlockEntity getBlockProps(@NonNull String blockHash) {
		return blockDao.loadAllByHash(blockHash).get(0);
	}

	public boolean getBlockExistsOnDisk(@NonNull String blockHash) {
		Log.i(TAG, String.format("\nGET BLOCK EXISTS called with blockHash='"+blockHash+"'"));

		try {
			//Get the location of the block on disk
			File blockFile = getBlockLocationOnDisk(blockHash);
			return blockFile.exists();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	public Uri getBlockUri(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK URI called with blockHash='"+blockHash+"'"));

		//TODO Does this work? Does it return null or something else
		if(blockDao.loadByHash(blockHash) == null)
			throw new FileNotFoundException("Block does not exist! Hash='"+blockHash+"'");

		//Get the location of the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);
		return Uri.fromFile(blockFile);
	}

	@NonNull
	public byte[] readBlock(@NonNull String blockHash)
			throws IOException {
		Log.i(TAG, String.format("\nREAD BLOCK called with blockHash='"+blockHash+"'"));

		Uri blockUri = getBlockUri(blockHash);
		File blockFile = new File(blockUri.getPath());

		//Read the block data from the file
		try (FileInputStream fis = new FileInputStream(blockFile)) {
			byte[] bytes = new byte[(int) blockFile.length()];
			fis.read(bytes);
			return bytes;
		}
	}


	public void writeBlock(@NonNull String blockHash, @NonNull byte[] bytes) throws IOException {
		Log.i(TAG, String.format("\nWRITE BLOCK called with blockHash='"+blockHash+"'"));

		//Get the location of the block on disk
		File blockFile = getBlockLocationOnDisk(blockHash);


		//Write the block data to the file
		try (FileOutputStream fos = new FileOutputStream(blockFile)) {
			fos.write(bytes);
		}


		//Create a new entry in the block table
		LBlockEntity blockEntity = new LBlockEntity(blockHash, bytes.length);
		blockDao.put(blockEntity);

		Log.i(TAG, "Uploading block complete");
	}


	private File getBlockLocationOnDisk(@NonNull String hash) throws IOException {
		Context context = MyApplication.getAppContext();

		//Starting out of the app's data directory...
		String appDataDir = context.getApplicationInfo().dataDir;

		//Blocks are stored in a block subdirectory
		File blockRoot = new File(appDataDir, blockDir);
		if(!blockRoot.isDirectory())
			blockRoot.mkdir();

		//With each block named by its SHA256 hash
		File blockFile = new File(blockRoot, hash);
		blockFile.createNewFile();
		return blockFile;
	}
}
