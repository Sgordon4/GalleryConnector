package com.example.galleryconnector.repositories.combined.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.UUID;


public class DomainOpWorker extends Worker {
	private static final String TAG = "Gal.DomWorker";
	private final DomainAPI domainAPI;


	public DomainOpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
	}


	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "DomainOpWorker doing work");

		String fileUIDString = getInputData().getString("FILEUID");
		assert fileUIDString != null;
		UUID fileUID = UUID.fromString(fileUIDString);

		String operationsMapString = getInputData().getString("OPERATIONS");
		assert operationsMapString != null;
		int operationsMap = Integer.parseInt(operationsMapString);



		try {
			//Note: Having something like both COPY_TO_LOCAL and COPY_TO_SERVER is technically valid
			try {
				if((operationsMap & DomainAPI.COPY_TO_LOCAL) > 0)
					domainAPI.copyFileToLocal(fileUID);
				if((operationsMap & DomainAPI.COPY_TO_SERVER) > 0)
					domainAPI.copyFileToServer(fileUID);
			} catch (IllegalStateException e) {
				Log.i(TAG, "File already exists at destination! Skipping copy operation.");
				//Hashes don't match, but since we pass in null in the copy methods this means
				// the file already exists at its destination. Job done.
			}


			if((operationsMap & DomainAPI.REMOVE_FROM_LOCAL) > 0)
				domainAPI.removeFileFromLocal(fileUID);
			if((operationsMap & DomainAPI.REMOVE_FROM_SERVER) > 0)
				domainAPI.removeFileFromServer(fileUID);



			//TODO If this fails with a timeout, requeue it

		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}
}
