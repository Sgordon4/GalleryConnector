package com.example.galleryconnector.repositories.local;

import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.local.file.LFileEntity;

import java.util.ArrayList;
import java.util.List;

public class LocalFileObservers {

	private final List<LFileObservable> listeners;

	public LocalFileObservers() {
		listeners = new ArrayList<>();
	}


	public void addObserver(LFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(LFileObservable observer) {
		listeners.remove(observer);
	}


	public void notifyObservers(int journalID, LFileEntity file) {
		for (LFileObservable listener : listeners) {
			listener.onFileUpdate(journalID, file);
		}
	}


	public interface LFileObservable {
		void onFileUpdate(int journalID, LFileEntity file);
	}
}
