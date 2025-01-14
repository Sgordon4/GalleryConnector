package com.example.galleryconnector.repositories.combined.jobs.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;

import java.net.ConnectException;
import java.util.UUID;


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
			GFile file = syncHandler.trySync(fileUID);
			if(file == null)
				Log.w(TAG, "Nothing to sync!");
			else
				Log.w(TAG, "SyncWorker was successful!");
		}
		//If the sync fails due to another update happening before we could finish the sync, requeue it for later
		catch (IllegalStateException e) {
			Log.w(TAG, "SyncWorker requeueing due to conflicting update!");
			return Result.retry();
		}
		//If the sync fails due to server connection issues, requeue it for later
		catch (ConnectException e) {
			Log.w(TAG, "SyncWorker requeueing due to connection issues!");
			return Result.retry();
		}

		return Result.success();
	}
}
