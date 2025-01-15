package com.example.galleryconnector.repositories.combined;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerFileObservers;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.combined.jobs.sync.SyncHandler;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GFileUpdateObservers {

	private static final String TAG = "Gal.GRepo.GUpObserve";

	private final List<GFileObservable> listeners;
	private final SyncHandler syncHandler;

	public GFileUpdateObservers() {
		listeners = new ArrayList<>();
		this.syncHandler = SyncHandler.getInstance();
	}

	public void attachListeners(@NonNull LocalRepo lRepo, @NonNull ServerRepo sRepo) {
		int localJournalStartID = syncHandler.getLastSyncLocal();
		int serverJournalStartID = syncHandler.getLastSyncServer();

		UUID currentLoggedInAccount = UUID.randomUUID();	//Doesn't actually do anything atm


		attachToLocal(lRepo, localJournalStartID, currentLoggedInAccount);
		attachToServer(sRepo, serverJournalStartID, currentLoggedInAccount);
	}


	public void addObserver(GFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(GFileObservable observer) {
		listeners.remove(observer);
	}



	private void onLocalFileUpdate(int journalID, @NonNull GFile file) {
		//Queue this file to be synced local <-> server
		syncHandler.enqueue(file.fileuid);

		//Update the latest synced journalID
		syncHandler.updateLastSyncLocal(journalID);

		Log.i(TAG, "Local file with journalID '"+journalID+"' updated! File: "+file.fileuid);

		//Notify the observers of the update
		notifyObservers(file);
	}

	//Perhaps on getting an update from server, compare to local (cheap) to prevent unnecessary update notifications
	//Will probably be obsoleted once we add prevHash checking noted in the comment above
	private void onServerFileUpdate(int journalID, @NonNull GFile file) {
		//Queue this file to be synced local <-> server
		syncHandler.enqueue(file.fileuid);

		//Update the latest synced journalID
		syncHandler.updateLastSyncServer(journalID);

		System.out.println("Server file with journalID '"+journalID+"' updated! File: "+file.fileuid);

		//Notify the observers of the update
		notifyObservers(file);
	}



	public void attachToLocal(@NonNull LocalRepo localRepo, int startJournalID, UUID accountUID) {
		localRepo.setFileListener(startJournalID, newJournal -> {
			Thread thread = new Thread(() -> {
				try {
					//Get the file that the journal is linked to	TODO Authenticate
					LFile file = localRepo.getFileProps(newJournal.fileuid);

					//Notify listeners
					GFile gFile = GFile.fromLocalFile(file);
					onLocalFileUpdate(newJournal.journalid, gFile);

				} catch (FileNotFoundException e) {
					//File was likely deleted somewhere along the line
					//throw new RuntimeException(e);
				}
			});
			thread.start();
		});
	}
	public void attachToServer(@NonNull ServerRepo serverRepo, int startJournalID, UUID accountUID) {
		ServerFileObservers.SFileObservable sFileChangedObs = (journalID, serverFile) -> {
			Thread thread = new Thread(() -> {
				//Notify listeners
				GFile file = new Gson().fromJson(serverFile.toJson(), GFile.class);
				onServerFileUpdate(journalID, file);
			});
			thread.start();
		};

		serverRepo.addObserver(sFileChangedObs);
		serverRepo.startListening(startJournalID, accountUID);
	}



	public void notifyObservers(GFile file) {
		for (GFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	public interface GFileObservable {
		void onFileUpdate(GFile file);
	}
}
