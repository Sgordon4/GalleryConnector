package com.example.galleryconnector.repositories.local;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.HashMap;
import java.util.Map;

//Thanks ChatGPT

/**
 * Helper class to listen for updates on a Room database using LiveData.
 */
public class RoomDatabaseUpdateListener {

	private final Map<LiveData<?>, Observer<Object>> observers = new HashMap<>();

	/**
	 * Start listening for updates on a LiveData object.
	 *
	 * @param liveData   The LiveData instance to observe.
	 * @param onChanged  The callback to invoke when data changes.
	 * @param <T>        The type of data observed.
	 */
	public <T> void listen(LiveData<T> liveData, OnDataChangeListener<T> onChanged) {
		// Ensure that we only add a single observer per LiveData instance.
		if (observers.containsKey(liveData)) return;

		Observer<Object> observer = data -> {
			@SuppressWarnings("unchecked")
			T typedData = (T) data;
			onChanged.onDataChanged(typedData);
		};
		liveData.observeForever(observer);
		observers.put(liveData, observer);
	}


	/**
	 * Stop listening for updates on a specific LiveData object.
	 *
	 * @param liveData The LiveData instance to stop observing.
	 */
	public void stopListening(LiveData<?> liveData) {
		Observer<Object> observer = observers.remove(liveData);
		if (observer != null) {
			liveData.removeObserver(observer);
		}
	}

	/**
	 * Stop listening for updates on all LiveData objects.
	 */
	public void stopAll() {
		for (Map.Entry<LiveData<?>, Observer<Object>> entry : observers.entrySet()) {
			entry.getKey().removeObserver(entry.getValue());
		}
		observers.clear();
	}

	/**
	 * Callback interface for data change events.
	 *
	 * @param <T> The type of data observed.
	 */
	public interface OnDataChangeListener<T> {
		void onDataChanged(T data);
	}
}