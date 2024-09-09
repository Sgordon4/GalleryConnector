package com.example.galleryconnector.server;

import com.example.galleryconnector.local.file.LFileEntity;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SFileUpdateObservers {

	private List<SFileObservable> listeners;

	public SFileUpdateObservers() {
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
