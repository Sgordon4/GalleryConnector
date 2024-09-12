package com.example.galleryconnector;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.example.galleryconnector.repositories.local.LocalFileObservers;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.movement.ImportExportWorker;
import com.example.galleryconnector.repositories.server.ServerFileObservers;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class GFileUpdateObservers {

	private final List<GFileObservable> listeners;
	private final Context context;

	public GFileUpdateObservers(@NonNull Context context, @NonNull LocalRepo lRepo, @NonNull ServerRepo sRepo) {
		listeners = new ArrayList<>();
		this.context = context;

		attachToLocal(lRepo);
		attachToServer(sRepo);
	}


	public void addObserver(GFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(GFileObservable observer) {
		listeners.remove(observer);
	}



	private void onFileUpdate(@NonNull JsonObject file) {
		//Now that we know there's been an update, start a sync (does nothing if one already exists)
		launchSyncWorker();

		notifyObservers(file);
	}


	public void attachToLocal(@NonNull LocalRepo localRepo) {
		LocalFileObservers.LFileObservable lFileChangedObs = file -> {
			JsonObject fileJson = new Gson().toJsonTree(file).getAsJsonObject();
			onFileUpdate(fileJson);
		};

		localRepo.addObserver(lFileChangedObs);
	}
	public void attachToServer(@NonNull ServerRepo serverRepo) {
		ServerFileObservers.SFileObservable sFileChangedObs = file -> {
			//TODO Perhaps on getting an update from server, compare to local (cheap) to prevent unnecessary update notifications
			onFileUpdate(file);
		};

		serverRepo.addObserver(sFileChangedObs);
	}


	private void launchSyncWorker() {
		//TODO Be sure to set this up to ignore if a sync is already in progress
		//The WorkRequest can be configured
		WorkRequest syncRequest = new OneTimeWorkRequest.Builder(ImportExportWorker.class).build();
		WorkManager.getInstance(context).enqueue(syncRequest);
	}



	public void notifyObservers(JsonObject file) {
		for (GFileObservable listener : listeners) {
			listener.onFileUpdate(file);
		}
	}


	public interface GFileObservable {
		void onFileUpdate(JsonObject file);
	}
}
