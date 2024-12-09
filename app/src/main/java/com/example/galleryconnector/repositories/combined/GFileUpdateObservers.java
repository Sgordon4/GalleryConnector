package com.example.galleryconnector.repositories.combined;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerFileObservers;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.combined.sync.SyncHandler;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
	private void onLocalFileUpdate(int journalID, @NonNull GFile file) {
		//Now that we know there's been an update, start a sync. OK to have multiple consecutive syncs for same file
		try {
			//Try to sync this file's data local <-> server
			boolean dataWritten = syncHandler.trySync(file.fileuid);

			//Update the latest synced journalID
			syncHandler.updateLastSyncLocal(journalID);

			if(dataWritten) {
				//Notify the observers of the update
				notifyObservers(journalID, file);
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	//Perhaps on getting an update from server, compare to local (cheap) to prevent unnecessary update notifications
	//Will probably be obsoleted once we add prevHash checking noted in the comment above
	private void onServerFileUpdate(int journalID, @NonNull GFile file) {
		//Now that we know there's been an update, start a sync. OK to have multiple consecutive syncs for same file
		try {
			//Try to sync this file's data local <-> server
			boolean dataWritten = syncHandler.trySync(file.fileuid);

			//Update the latest synced journalID
			syncHandler.updateLastSyncServer(journalID);

			if(dataWritten) {
				//Notify the observers of the update
				notifyObservers(journalID, file);
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
	}



	public void attachToLocal(@NonNull LocalRepo localRepo, int journalID) {
		localRepo.setFileListener(journalID, newJournal -> {
			try {
				//Get the file that the journal is linked to	TODO Authenticate
				LFile file = localRepo.getFileProps(newJournal.fileuid);
				if(file == null) throw new IllegalStateException("File not found! ID: '"+newJournal.fileuid+"'");

				//Notify listeners
				GFile gFile = GFile.fromLocalFile(file);
				onLocalFileUpdate(newJournal.journalid, gFile);

			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}
	public void attachToServer(@NonNull ServerRepo serverRepo) {
		ServerFileObservers.SFileObservable sFileChangedObs = (journalID, serverFile) -> {
			//Notify listeners
			GFile file = new Gson().fromJson(serverFile.toJson(), GFile.class);
			onServerFileUpdate(journalID, file);
		};

		serverRepo.addObserver(sFileChangedObs);
	}



	public void notifyObservers(int journalID, GFile file) {
		for (GFileObservable listener : listeners) {
			listener.onFileUpdate(journalID, file);
		}
	}


	public interface GFileObservable {
		void onFileUpdate(int journalID, GFile file);
	}
}
