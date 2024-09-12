package com.example.galleryconnector.movement;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.UUID;

public class ImportExportApi {
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



	public JsonObject importFileToLocal(@NonNull UUID accountuid, @NonNull UUID parent, @NonNull Uri source) {
		Context context = MyApplication.getAppContext();

		//Import the file to the local system, starting with baseline file properties
		LFileEntity file = new LFileEntity(accountuid);
		try {
			localRepo.uploadFile(file, source, context);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		//Now that the file is imported, we need to add it to the directory
		//TODO Add the new file to parent's ordering



		//TODO As a property of each dir (inside the dir file), make note of the preferred domain (l, l+s, s).
		// This would just be set when moving the dir around I guess. idk about this one.
		// We want the new file to follow the parent's example for which repositories to sit in
		// For now we'll just leave it local
		//MovementHandler.getInstance().domainAPI.queueOperation();


		return new Gson().toJsonTree(file).getAsJsonObject();
	}

	public JsonObject exportFile(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		throw new RuntimeException("Stub!");
	}
}
