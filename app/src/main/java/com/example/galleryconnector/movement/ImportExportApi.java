package com.example.galleryconnector.movement;

import static com.example.galleryconnector.repositories.local.block.LBlockHandler.CHUNK_SIZE;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ImportExportApi {
	private static final String TAG = "Gal.IOAPI";
	private final LocalRepo localRepo;


	public static ImportExportApi getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ImportExportApi INSTANCE = new ImportExportApi();
	}
	private ImportExportApi() {
		localRepo = LocalRepo.getInstance();
	}



	//Import a file to the local system from a uri.
	//Upon a successful import, the file will be moved between local/server based on its parent.
	public LFileEntity importFileToLocal(@NonNull UUID accountuid, @NonNull UUID parent, @NonNull Uri source) {
		Context context = MyApplication.getAppContext();


		//Import the blockset to the local repository
		JsonObject blocksetData = importUriToBlocks(source, context);


		//Make a brand new file entity
		LFileEntity file = new LFileEntity(accountuid);

		//Update the entity with the blockset information
		Type listType = new TypeToken<List<String>>() {}.getType();
		file.fileblocks = new Gson().fromJson(blocksetData.get("fileblocks"), listType);
		file.filehash = blocksetData.get("filehash").getAsString();
		file.filesize = blocksetData.get("filesize").getAsInt();

		//And write the new file entity to the database
		localRepo.database.getFileDao().put(file);



		//Now that the file is imported, we need to add it to the directory
		//TODO Add the new file to parent's ordering


		//TODO As a property of each dir (inside the dir file), make note of the preferred domain (l, l+s, s).
		// This would just be set manually or when moving the dir around I guess. idk about this one.
		// We want the new file to follow the parent's example for which repositories to sit in
		// Until that system is in place, we'll just leave it local
		//MovementHandler.getInstance().domainAPI.queueOperation();


		return file;
	}


	//Should this also do work on server? Probably, right?
	public JsonObject exportFileFromLocal(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		throw new RuntimeException("Stub!");
	}


	//---------------------------------------------------------------------------------------------


	//Import a file from a uri to the local block repository & table
	private JsonObject importUriToBlocks(@NonNull Uri source, @NonNull Context context) {
		Log.i(TAG, String.format("IMPORT BLOCKSET called with uri='%s'", source));
		ContentResolver contentResolver = context.getContentResolver();

		//The destination system may already have some/all of the blocks in this uri.
		//We need to know what blocks in the blocklist local is missing.
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

				//Trim block if needed (for tail of the file, when not enough bytes to fill a full block)
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


				//TODO Check that we actually get null if a block is missing, and not something else
				//If the block is not already present in the system, we need to add it
				if(localRepo.blockHandler.getBlock(hashString) == null)
					localRepo.blockHandler.writeBlock(hashString, block);


				//Add to the hash list
				fileHashes.add(hashString);
			}

			//Get the SHA-256 hash of the entire file
			filehash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}


		//Package everything up and return it
		JsonObject blocksetData = new JsonObject();//new Gson().toJsonTree(fileHashes).getAsJsonObject();
		blocksetData.add("fileblocks", new Gson().toJsonTree(fileHashes));
		blocksetData.addProperty("filehash", filehash);
		blocksetData.addProperty("filesize", Integer.toString(filesize));

		Log.d(TAG, "Imported Data: "+blocksetData);

		return blocksetData;
	}
}
