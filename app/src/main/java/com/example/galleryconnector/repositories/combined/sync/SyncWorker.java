package com.example.galleryconnector.repositories.combined.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.galleryconnector.repositories.combined.movement.DomainAPI;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class SyncWorker extends Worker {
	private static final String TAG = "Gal.SyncWorker";
	private final SyncHandler syncHandler;


	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		syncHandler = SyncHandler.getInstance();
	}


	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "SyncWorker doing work");

		String fileUIDString = getInputData().getString("FILEUID");
		assert fileUIDString != null;
		UUID fileUID = UUID.fromString(fileUIDString);


		try {
			syncHandler.trySync(fileUID);

			//TODO If this fails for almost any reason, requeue it
		} catch (ExecutionException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}
}
