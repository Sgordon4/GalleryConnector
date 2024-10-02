package com.example.galleryconnector.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.UUID;


public class DomainOpWorker extends Worker {
	private static final String TAG = "Gal.DOp";
	private final DomainAPI domainAPI;


	public DomainOpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
	}


	//TODO Make sure to rerun if no internet

	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "DomainOpWorker doing work");

		String operationString = getInputData().getString("OPERATION");
		assert operationString != null;
		DomainAPI.Operation operation = DomainAPI.Operation.valueOf(operationString);

		String fileUIDString = getInputData().getString("FILEUID");
		assert fileUIDString != null;
		UUID fileUID = UUID.fromString(fileUIDString);


		try {
			switch (operation) {
				case COPY_TO_LOCAL:
					domainAPI.copyFileToLocal(fileUID);
					break;
				case REMOVE_FROM_LOCAL:
					domainAPI.removeFileFromLocal(fileUID);
					break;
				case COPY_TO_SERVER:
					domainAPI.copyFileToServer(fileUID);
					break;
				case REMOVE_FROM_SERVER:
					domainAPI.removeFileFromServer(fileUID);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}
}
