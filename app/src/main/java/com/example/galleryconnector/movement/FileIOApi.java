package com.example.galleryconnector.movement;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class FileIOApi {
	private final File queueFile;
	private final ReentrantLock lock;


	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;


	public static FileIOApi getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final FileIOApi INSTANCE = new FileIOApi();
	}
	private FileIOApi() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		//We need a synchronous read/write lock for the operations file
		lock = new ReentrantLock();

		Context context = MyApplication.getAppContext();
		queueFile = new File(context.getDataDir(), "IOQueue.txt");

		if(!queueFile.exists()) {
			try {
				queueFile.createNewFile();
			} catch (Exception e) {
				throw new RuntimeException("IO Queue file could not be created!");
			}
		}
	}



	public boolean queueImportFile(Uri source, UUID destDir, UUID account) {
		//Build the queue item to add to the queue file
		JsonObject newImportItem = new JsonObject();
		newImportItem.addProperty("operation", "import");
		newImportItem.addProperty("source", source.toString());
		newImportItem.addProperty("parent", destDir.toString());
		newImportItem.addProperty("account", account.toString());

		//Add the new item to the queue
		queueItem(newImportItem);

		return true;
	}

	public boolean queueExportFile(UUID file, UUID parent, Uri dest) {
		//Build the queue item to add to the queue file
		JsonObject newExportItem = new JsonObject();
		newExportItem.addProperty("operation", "export");
		newExportItem.addProperty("source", file.toString());
		newExportItem.addProperty("parent", parent.toString());
		newExportItem.addProperty("destination", dest.toString());

		//Add the new item to the queue
		queueItem(newExportItem);

		return true;
	}


	private void queueItem(JsonObject newQueueItem) {
		//Lock the file before we make any reads/writes
		lock.lock();

		try (BufferedReader reader = new BufferedReader(new FileReader(queueFile))) {
			//Read the queue into a JsonObject
			JsonObject queueObj = new Gson().fromJson(reader, JsonObject.class);
			if(queueObj == null) queueObj = new JsonObject();

			//Get the queue itself
			JsonElement element = queueObj.get("queue");
			if(element == null) element = new JsonArray();


			//Add the new item to the queue
			JsonArray queue = element.getAsJsonArray();
			queue.add(newQueueItem);
			queueObj.add("queue", queue);


			//And write the json back to the file
			try (FileWriter writer = new FileWriter(queueFile)) {
				writer.write(new Gson().toJson(queueObj));
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}


	//---------------------------------------------------------------------------------------------


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
