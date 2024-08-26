package com.example.galleryconnector.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.UUID;

public class DomainOpWorker extends Worker {
	private static final String TAG = "Gal.DOp";
	private final DomainAPI domainAPI;


	public DomainOpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
	}


	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "DomainOpWorker doing work");

		boolean addingToQueue;
		DomainAPI.Operation operation;
		UUID fileUID;

		try {
			String queueing = getInputData().getString("QUEUEING");
			String operationStr = getInputData().getString("OPERATION");
			String file = getInputData().getString("FILEUID");

			assert queueing != null;
			assert operationStr != null;
			assert file != null;

			addingToQueue = Boolean.getBoolean( queueing );
			operation = DomainAPI.Operation.valueOf( operationStr );
			fileUID = UUID.fromString( file );

		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Invalid parameter passed to QueueMoveWorker!");
			throw new RuntimeException(e);
		}

		return null;
	}
}
