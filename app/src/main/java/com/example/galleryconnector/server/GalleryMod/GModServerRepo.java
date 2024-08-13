package com.example.galleryconnector.server.GalleryMod;


import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.gallerymodularized.MyApplication;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//https://google.github.io/volley/
//https://developer.android.com/develop/connectivity/cronet

//TODO Maybe don't check for exists before operating for efficiency, maybe just error on return
final public class GModServerRepo {
	private static final String TAG = "Gal.SRepo";

	static final int CHUNK_SIZE = 1024 * 1024 * 5;  //5MB

	private final GModServerConnector connector;
	private final Path cacheDir;

	//TODO Definitely turn this into a room db for slightly more persistent caching and garbage collection
	Map<UUID, JsonObject> accountCache;
	Map<UUID, JsonObject> filePropCache;


	private GModServerRepo() {
		connector = new GModServerConnector();
		cacheDir = Paths.get(MyApplication.getAppContext().getCacheDir().getPath(), "Server");

		accountCache = new HashMap<>();
		filePropCache = new HashMap<>();
	}
	public static GModServerRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GModServerRepo INSTANCE = new GModServerRepo();
	}



	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	public JsonObject getAccount(@NonNull UUID accountID) {
		Log.d(TAG, "getAccount: Fetching account props for accountUID='"+accountID+"'");

		//If this is already cached, just return that
		if(accountCache.containsKey(accountID)) {
			Log.d(TAG, "getAccount: Account props are already cached, returning that.");
			return accountCache.get(accountID);
		}

		//Otherwise go get it, add it to the cache, and then return it
		JsonObject account = connector.getAccount(accountID);
		accountCache.put(accountID, account);
		return account;
	}



	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public JsonObject createFile(@NonNull UUID parentUID, boolean isDir, boolean isLink) {
		Log.d(TAG, "createFile: Creating file");
		return connector.createFile(parentUID, isDir, isLink);
	}


	public Uri downloadFile(@NonNull UUID fileUID) {
		Log.d(TAG, "downloadFile: Downloading file for fileUID='"+fileUID+"'");

		//If we already have the file cached, return it
		File cachedFileLocation = getCachedFileLocation(fileUID);
		if(cachedFileLocation.exists()) {
			Log.d(TAG, "File is already cached, returning that.");
			return Uri.fromFile(cachedFileLocation);
		}

		//Otherwise, we need to download it to the cache and then return it
		try {
			return forceCacheFile(fileUID);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public JsonObject getFileProperties(@NonNull UUID fileUID) throws IOException {
		Log.d(TAG, "getFileProperties: Fetching file properties for fileUID='"+fileUID+"'");
		//If this is already cached, just return that
		if(filePropCache.containsKey(fileUID)) {
			Log.d(TAG, "getAccount: File properties are already cached, returning that.");
			return filePropCache.get(fileUID);
		}

		//Otherwise go get it, add it to the cache, and then return it
		JsonObject fileProps = connector.getFileProps(fileUID);
		accountCache.put(fileUID, fileProps);
		return fileProps;
	}

	//Will need this for syncing
	public byte[] getBlock(String blockHash) {
		throw new RuntimeException("Stub!");
	}



	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	private File getCachedFileLocation(@NonNull UUID fileUID) {
		return new File(cacheDir.toString(), fileUID.toString());

	}

	//TODO I'm thinking about making a process to compile the file on the server end, and then
	// sending the whole thing over here. Would maybe use okhttp's new HttpResponse.BodyHandlers.ofFile.
	// However, that would put more load on the server, and might just be better done here.
	private Uri forceCacheFile(@NonNull UUID fileUID) throws IOException {
		//Make sure the cache dir exists
		if(!cacheDir.toFile().exists())
			cacheDir.toFile().mkdir();

		//Write the cache file to the cache dir, with fileUID as the filename
		File destination = getCachedFileLocation(fileUID);
		//Even if the file already exists, the purpose of this method is to re-cache it



		Log.i(TAG, "Caching file '"+fileUID+"'");
		//Get the file's blockset from the server
		JsonObject fileProps = connector.getFileProps(fileUID);
		List<String> fileblocks = new Gson().fromJson(fileProps.get("fileblocks"),
				new TypeToken< List<String> >(){}.getType());


		Log.i(TAG, String.format("Caching file %s to %s", fileUID, destination.getPath()));
		try(OutputStream os = new FileOutputStream(destination)) {
			//For each block in the file's blockset...
			os.write("".getBytes());
			for(String blockHash : fileblocks) {
				//Download the block from the server and write it to the file
				byte[] block = connector.getBlock(blockHash);
				os.write(block);
			}
		}

		return Uri.fromFile(destination);
	}


	//---------------------------------------------------------------------------------------------


	public void uploadFile(@NonNull UUID fileUID, @NonNull String contents) throws IOException {
		List<String> fileHashes = new ArrayList<>();
		try {
			//Hash the block
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(contents.getBytes());
			String hashString = bytesToHex(hash);

			//Needs to be in list form to be consumed by commitFileBlockset
			if (!contents.isEmpty())   //Don't put empty blocks in the blocklist
				fileHashes.add(hashString);

		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}

		//Now try to upload/commit the block
		List<String> missingBlocks = new ArrayList<>();
		int tries = 4 +1;
		do {
			//If our block is missing, upload it
			for(String missingBlock : missingBlocks) {
				System.out.println("Missing "+missingBlock);
				connector.uploadBlock(missingBlock, contents.getBytes());
			}

			//Attempt to commit the blocklist to the server
			System.out.println("Attempting to commit to "+fileUID);
			missingBlocks = connector.commitFileBlockset(fileUID, fileHashes);

			tries -= 1;
		}
		while(!missingBlocks.isEmpty() && tries > 0);

		System.out.println("Commit successful");



		//TODO Maybe pull out some code from cacheFile for this
		//Now cache the file
		cacheDir.toFile().mkdir();
		File cacheFile = getCachedFileLocation(fileUID);
		cacheFile.createNewFile();
		try(OutputStream os = new FileOutputStream(cacheFile)) {
			System.out.println("Writing to cache file");
			os.write(contents.getBytes());
		}


		System.out.println("Are we there yet?");
	}


	//-------------------------------------------------


	public void uploadFile(@NonNull UUID fileUID, @NonNull Uri source) throws IOException {
		ContentResolver contentResolver = MyApplication.getAppContext().getContentResolver();
		System.out.println("Attempting to upload");

		//We need to know what blocks in the blocklist the server is missing.
		//To do that, we need to attempt to commit the file blocklist to the server.
		//To do that, we need the blocklist. Get the blocklist:
		List<String> fileHashes = new ArrayList<>();
		try (InputStream is = contentResolver.openInputStream(source)) {
			//Read the next block
			byte[] block = new byte[CHUNK_SIZE];
			int read;
			while((read = is.read(block)) != -1) {
				//Trim block if needed
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;

				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = bytesToHex(hash);

				//Add to the hash list
				fileHashes.add(hashString);
			}
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		System.out.println("FileHashes: \n"+fileHashes);


		//Now try to upload/commit blocks
		List<String> missingBlocks = new ArrayList<>();
		int tries = 4 +1;
		do {
			//If any blocks are missing, upload them (skipped on first run)
			for(String missingBlock : missingBlocks) {
				//Go to the correct block
				int index = fileHashes.indexOf(missingBlock);
				int blockStart = index * CHUNK_SIZE;

				System.out.println("Reading block at "+blockStart+" = '"+missingBlock+"'");
				try (InputStream is = contentResolver.openInputStream(source)) {
					//Read the missing block
					is.skip(blockStart);
					byte[] block = new byte[CHUNK_SIZE];
					int read = is.read(block);

					//Trim block if needed
					if (read != CHUNK_SIZE) {
						byte[] smallerData = new byte[read];
						System.arraycopy(block, 0, smallerData, 0, read);
						block = smallerData;
					}

					//Throw it to the wind
					connector.uploadBlock(missingBlock, block);
				} catch (IOException e) {
					System.out.println("Block upload failed with hash "+missingBlock);
					e.printStackTrace();
				}
			}

			//Attempt to commit the blocklist to the server
			System.out.println("Committing blocklist");
			missingBlocks = connector.commitFileBlockset(fileUID, fileHashes);

			tries -= 1;
		}
		while(!missingBlocks.isEmpty() && tries > 0);

		System.out.println("Successful upload!");


		//TODO Maybe pull out some code from cacheFile for this
		//Now cache the file
		cacheDir.toFile().mkdir();
		File cacheFile = getCachedFileLocation(fileUID);
		cacheFile.createNewFile();

		try (InputStream in = contentResolver.openInputStream(source)) {
			if(in == null)
				throw new IOException("InputStream is null! Uri='"+source+"'");

			try (OutputStream out = new FileOutputStream(cacheFile)) {
				System.out.println("Writing to cache file");

				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}
		}


		System.out.println("Are we there yet?");
	}



	//https://stackoverflow.com/a/9855338
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	public static String bytesToHex(@NonNull byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}

}
