package com.example.galleryconnector;

import com.example.galleryconnector.local.LFileUpdateObservers;
import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class GFileUpdateObservers {

	private List<GFileObservable> listeners;

	public GFileUpdateObservers() {
		listeners = new ArrayList<>();
	}


	public void addObserver(GFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(GFileObservable observer) {
		listeners.remove(observer);
	}


	public void attachToLocal(LocalRepo localRepo) {
		LFileUpdateObservers.LFileObservable lFileObservable = file -> {
			JsonObject fileJson = new Gson().toJsonTree(file).getAsJsonObject();
			notifyObservers(fileJson);

			//TODO Launch sync
		};

		localRepo.addObserver(lFileObservable);
	}
	public void attachToServer(ServerRepo serverRepo) {
		//TODO Perhaps on getting an update from server, compare to local to prevent unnecessary update notifications
	}


	public void notifyObservers(JsonObject file) {
		for (GFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	interface GFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
