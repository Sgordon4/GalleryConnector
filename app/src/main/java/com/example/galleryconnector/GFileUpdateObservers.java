package com.example.galleryconnector;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.local.LocalFileObservers;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.server.ServerFileObservers;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.sync.SyncHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class GFileUpdateObservers {

	private final List<GFileObservable> listeners;
	private final SyncHandler syncHandler;

	public GFileUpdateObservers(@NonNull LocalRepo lRepo, @NonNull ServerRepo sRepo) {
		listeners = new ArrayList<>();
		this.syncHandler = SyncHandler.getInstance();

		attachToLocal(lRepo);
		attachToServer(sRepo);
	}


	public void addObserver(GFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(GFileObservable observer) {
		listeners.remove(observer);
	}



	//TODO Once we add in hash checking, we could skip all the sync checking mumbo jumbo and just have these call
	// a new trySyncToServer() or trySyncToLocal() instead.
	private void onLocalFileUpdate(@NonNull JsonObject file) {
		//Now that we know there's been an update, start a sync. OK to have multiple consecutive syncs for same file
		try {
			//Try to sync this file's data local <-> server
			boolean dataWritten = syncHandler.trySync(UUID.fromString(file.get("fileUID").getAsString()));

			if(dataWritten) {
				//Notify the observers of the update
				notifyObservers(file);
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	//Perhaps on getting an update from server, compare to local (cheap) to prevent unnecessary update notifications
	//Will probably be obsoleted once we add prevHash checking noted in the comment above
	private void onServerFileUpdate(@NonNull JsonObject file) {
		//Now that we know there's been an update, start a sync. OK to have multiple consecutive syncs for same file
		try {
			//Try to sync this file's data local <-> server
			boolean dataWritten = syncHandler.trySync(UUID.fromString(file.get("fileUID").getAsString()));

			if(dataWritten) {
				//Notify the observers of the update
				notifyObservers(file);
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
	}



	public void attachToLocal(@NonNull LocalRepo localRepo) {
		LocalFileObservers.LFileObservable lFileChangedObs = (file, journalID) -> {
			//Notify listeners
			JsonObject fileJson = new Gson().toJsonTree(file).getAsJsonObject();
			onLocalFileUpdate(fileJson);

			//Update the latest synced journalID
			syncHandler.updateLastSyncLocal(journalID);
		};

		localRepo.addObserver(lFileChangedObs);
	}
	public void attachToServer(@NonNull ServerRepo serverRepo) {
		ServerFileObservers.SFileObservable sFileChangedObs = (file, journalID) -> {
			//Notify listeners
			onServerFileUpdate(file);

			//Update the latest synced journalID
			syncHandler.updateLastSyncServer(journalID);
		};

		serverRepo.addObserver(sFileChangedObs);
	}



	public void notifyObservers(JsonObject file) {
		for (GFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	public interface GFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
