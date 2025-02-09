package com.example.galleryconnector.repositories.combined.jobs.domain_movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.UUID;


public class DomainOpWorker extends Worker {
	private static final String TAG = "Gal.DomWorker";
	private final DomainAPI domainAPI;
	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;


	public DomainOpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();
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


		//For testing
		if((operationsMap & DomainAPI.COPY_TO_LOCAL) > 0)
			Log.d(TAG, "DomWorker is copying to local.");
		if((operationsMap & DomainAPI.COPY_TO_SERVER) > 0)
			Log.d(TAG, "DomWorker is copying to server.");
		if((operationsMap & DomainAPI.REMOVE_FROM_LOCAL) > 0)
			Log.d(TAG, "DomWorker is removing from local.");
		if((operationsMap & DomainAPI.REMOVE_FROM_SERVER) > 0)
			Log.d(TAG, "DomWorker is removing from server.");



		try {
			//Note: Having something like both COPY_TO_LOCAL and COPY_TO_SERVER is technically valid.
			// This is fine because the worker won't send any content over since it already exists,
			// and will then attempt to create the file props, which will fail as they already exist.
			try {
				if((operationsMap & DomainAPI.COPY_TO_LOCAL) > 0) {
					Log.v(TAG, "DomWorker copying file to local. FileUID: " + fileUID);
					try {
						SFile file = serverRepo.getFileProps(fileUID);
						LFile newFile = domainAPI.createFileOnLocal(file);
					} catch (FileNotFoundException e) {
						Log.d(TAG, "File not found on server. Skipping COPY_TO_LOCAL operation.");
					}
				}
				if((operationsMap & DomainAPI.COPY_TO_SERVER) > 0) {
					Log.v(TAG, "DomWorker copying file to server. FileUID: " + fileUID);
					try {
						LFile file = localRepo.getFileProps(fileUID);
						SFile newFile = domainAPI.createFileOnServer(file);
					} catch (FileNotFoundException e) {
						Log.d(TAG, "File not found on local. Skipping COPY_TO_SERVER operation.");
					}
				}
			} catch (IllegalStateException e) {
				Log.i(TAG, "File already exists in both repos! Skipping copy operations.");
				//Hashes don't match, but since createFileOn... uses "null" for each hash, it failing
				// means the file already exists at its destination. Job done.
			}



			if(((operationsMap & DomainAPI.REMOVE_FROM_LOCAL) > 0) &&
					((operationsMap & DomainAPI.REMOVE_FROM_SERVER) > 0)) {
				Log.w(TAG, "DomWorker instructed to remove from *BOTH* local and server. " +
						"FileUID: "+fileUID+"::"+operationsMap);
			}


			//Do server first in case we can't connect and we're trying to remove both. In that case we want to fail before removing one.
			if((operationsMap & DomainAPI.REMOVE_FROM_SERVER) > 0) {
				Log.v(TAG, "DomWorker removing file from server. FileUID: " + fileUID);
				domainAPI.removeFileFromServer(fileUID);
			}
			if((operationsMap & DomainAPI.REMOVE_FROM_LOCAL) > 0) {
				Log.v(TAG, "DomWorker removing file from local. FileUID: " + fileUID);
				domainAPI.removeFileFromLocal(fileUID);
			}

		}
		//If this fails due to server connection issues, requeue it for later
		catch (ConnectException e) {
			Log.w(TAG, "DomWorker retrying due to connection issues!");
			return Result.retry();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}
}
