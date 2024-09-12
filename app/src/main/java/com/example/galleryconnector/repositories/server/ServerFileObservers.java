package com.example.galleryconnector.repositories.server;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ServerFileObservers {

	private List<SFileObservable> listeners;

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


	public interface SFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
