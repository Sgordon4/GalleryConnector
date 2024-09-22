package com.example.galleryconnector.repositories.server;

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
	private final ExecutorService executor;
	private Future<?> longpoller;


	public ServerFileObservers() {
		listeners = new ArrayList<>();
		executor = Executors.newSingleThreadExecutor();
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


	//TODO Make a listener thread for longpoll
	public void startListening(int journalID, UUID accountUID) throws ExecutionException, InterruptedException {
		//If we're already listening, return
		if(longpoller != null)
			return;

		//Start listening
		longpoller = executor.submit(() -> {

			while (true) {

				try {
					longpoll(journalID);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			}


		});
		longpoller.get();
	}



	public void longpoll(int journalID) throws IOException {
		//Try to get any new journal entries
		List<JsonObject> entries = ServerRepo.getInstance().longpollJournalEntriesAfter(journalID);

		//If we get any entries back, notify the observers
		for(JsonObject entry : entries)
			notifyObservers(entry);
	}




	public interface SFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
