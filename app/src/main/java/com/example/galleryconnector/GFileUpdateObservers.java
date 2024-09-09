package com.example.galleryconnector;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

import com.example.galleryconnector.local.LFileUpdateObservers;
import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.movement.FileIOWorker;
import com.example.galleryconnector.server.SFileUpdateObservers;
import com.example.galleryconnector.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class GFileUpdateObservers {

	private List<GFileObservable> listeners;

	public GFileUpdateObservers(@NonNull Context context, @NonNull LocalRepo lRepo, @NonNull ServerRepo sRepo) {
		listeners = new ArrayList<>();

		attachToLocal(lRepo, context);
		attachToServer(sRepo, context);
	}


	public void addObserver(GFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(GFileObservable observer) {
		listeners.remove(observer);
	}


	public void attachToLocal(@NonNull LocalRepo localRepo, @NonNull Context context) {
		LFileUpdateObservers.LFileObservable lFileChangedObs = file -> {
			JsonObject fileJson = new Gson().toJsonTree(file).getAsJsonObject();
			notifyObservers(fileJson);

			//Now that we know there's been an update, start a sync
			//The WorkRequest can be configured
			WorkRequest syncRequest = new OneTimeWorkRequest.Builder(FileIOWorker.class).build();
			WorkManager.getInstance(context).enqueue(syncRequest);
		};

		localRepo.addObserver(lFileChangedObs);
	}
	public void attachToServer(@NonNull ServerRepo serverRepo, @NonNull Context context) {
		SFileUpdateObservers.SFileObservable sFileChangedObs = file -> {
			//TODO Perhaps on getting an update from server, compare to local (cheap) to prevent unnecessary update notifications

			notifyObservers(file);

			//Now that we know there's been an update, start a sync
			//The WorkRequest can be configured
			WorkRequest syncRequest = new OneTimeWorkRequest.Builder(FileIOWorker.class).build();
			WorkManager.getInstance(context).enqueue(syncRequest);
		};

		serverRepo.addObserver(sFileChangedObs);
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
