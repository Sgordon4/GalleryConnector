package com.example.galleryconnector.local;

import com.example.galleryconnector.local.file.LFileEntity;

import java.util.ArrayList;
import java.util.List;

public class LFileUpdateObservers {

	private List<LFileObservable> listeners;

	public LFileUpdateObservers() {
		listeners = new ArrayList<>();
	}


	public void addObserver(LFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(LFileObservable observer) {
		listeners.remove(observer);
	}


	public void notifyObservers(LFileEntity file) {
		for (LFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	public interface LFileObservable {
		void onFileUpdate(LFileEntity file);
	}
}
