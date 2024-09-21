package com.example.galleryconnector.repositories.server;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ServerFileObservers {

	private final List<SFileObservable> listeners;
	private boolean isListening = false;


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


	//TODO Make a listener thread for longpoll
	public boolean startListening() {
		if(isListening) return true;



	}




	public interface SFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
