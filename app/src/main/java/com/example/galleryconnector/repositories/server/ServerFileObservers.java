package com.example.galleryconnector.repositories.server;

import android.util.Log;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ServerFileObservers {

	private final List<SFileObservable> listeners;
	private Thread longpollThread;

	private static final String TAG = "Gal.SRepo.Obs";


	public ServerFileObservers() {
		listeners = new ArrayList<>();
	}


	public void addObserver(SFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(SFileObservable observer) {
		listeners.remove(observer);
	}


	public void notifyObservers(JsonObject file) {
		for (SFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	public void startListening(int journalID, UUID accountUID) {
		//If we're already listening, do nothing
		if(longpollThread != null && !longpollThread.isInterrupted() && longpollThread.isAlive())
			return;

		//Otherwise start perpetually longpolling the server for new journal entries
		Runnable runnable = () -> {
			int latestID = journalID;
			try {
				while (!Thread.currentThread().isInterrupted()) {
					Log.v(TAG, "Longpolling...");
					latestID = longpoll(latestID);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
		// Create and start the thread
		longpollThread = new Thread(runnable);
		longpollThread.start();
	}

	public void stopListening() {
		//If we're already listening, interrupt
		if(longpollThread != null && !longpollThread.isInterrupted() && longpollThread.isAlive())
			longpollThread.interrupt();
	}


	//Check the server for new journal entries. Returns the largest journal ID found.
	public int longpoll(int journalID) throws IOException {
		//Try to get any new journal entries. The request is designed to hang until new data is made
		List<JsonObject> entries = ServerRepo.getInstance().longpollJournalEntriesAfter(journalID);

		//If we get any entries back, notify the observers
		for(JsonObject entry : entries)
			notifyObservers(entry);



		//If we have any new data, return the largest journalID from the bunch (will always be last)
		if(!entries.isEmpty()) {
			return entries.get(entries.size() - 1).get("journalid").getAsInt();
		}

		//If no new data was found, don't update the latest journalID
		return journalID;
	}



	public interface SFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
