package com.example.galleryconnector.repositories.local;

import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.local.file.LFileEntity;

import java.util.ArrayList;
import java.util.List;

public class LocalFileObservers {

	private List<LFileObservable> listeners;

	public LocalFileObservers() {
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
