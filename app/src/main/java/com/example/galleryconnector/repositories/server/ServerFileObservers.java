package com.example.galleryconnector.repositories.server;

import android.util.Log;

import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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


	public void notifyObservers(int journalID, SJournal file) {
		for (SFileObservable listener : listeners) {
			listener.onFileUpdate(journalID, file);
		}
	}


	public void startListening(int journalID, UUID accountUID) {
		//If we're already listening, do nothing
		if(longpollThread != null && !longpollThread.isInterrupted() && longpollThread.isAlive())
			return;

		//Otherwise start perpetually longpolling the server for new journal entries
		Runnable runnable = () -> {
			int latestID = journalID;
			while (!Thread.currentThread().isInterrupted()) {
				//Log.v(TAG, "Longpolling...");
				try {
					latestID = longpoll(latestID);
				}
				catch (SocketTimeoutException e) {
					//This is supposed to happen, restart the poll
				}
				catch (SocketException e) {
					//Odd, but likely a server restart. Try to restart the poll
				}
				catch (IOException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						throw new RuntimeException(ex);
					}
					//throw new RuntimeException(e);
				}
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
		List<SJournal> entries = ServerRepo.getInstance().longpollJournalEntriesAfter(journalID);

		//If we get any entries back, notify the observers
		for(SJournal entry : entries) {
			int objJournalID = entry.journalid;
			notifyObservers(objJournalID, entry);
		}


		//If we have any new data, return the largest journalID from the bunch (will always be last)
		if(!entries.isEmpty()) {
			return entries.get(entries.size() - 1).journalid;
		}

		//If no new data was found, don't update the latest journalID
		return journalID;
	}



	public interface SFileObservable {
		void onFileUpdate(int journalID, SJournal file);
	}
}
