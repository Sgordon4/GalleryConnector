package com.example.galleryconnector.repositories.combined.movement;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.UnknownHostException;
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
	public LFile importFileToLocal(@NonNull UUID accountuid, @NonNull UUID parent, @NonNull Uri source) throws IOException {

		//Import the blockset to the local repository
		LBlockHandler.BlockSet blockSet = localRepo.putBlockData(source);


		//Make a brand new file entity, and update its block info
		LFile file = new LFile(accountuid);
		file.fileblocks = blockSet.blockList;
		file.filesize = blockSet.fileSize;
		file.filehash = blockSet.fileHash;

		//Write the new file entity to the database
		localRepo.putFileProps(file, null, null);



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
}
