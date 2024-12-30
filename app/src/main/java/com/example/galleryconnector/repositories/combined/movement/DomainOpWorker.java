package com.example.galleryconnector.repositories.combined.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;

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



		try {
			//Note: Having something like both COPY_TO_LOCAL and COPY_TO_SERVER is technically valid
			try {
				if((operationsMap & DomainAPI.COPY_TO_LOCAL) > 0) {
					Log.v(TAG, "DomWorker copying file to local. FileUID: " + fileUID);
					SFile file = serverRepo.getFileProps(fileUID);
					LFile newFile = domainAPI.createFileOnLocal(file);
				}
				if((operationsMap & DomainAPI.COPY_TO_SERVER) > 0) {
					Log.v(TAG, "DomWorker copying file to server. FileUID: " + fileUID);
					LFile file = localRepo.getFileProps(fileUID);
					SFile newFile = domainAPI.createFileOnServer(file);
				}
			} catch (IllegalStateException e) {
				Log.i(TAG, "File already exists at destination! Skipping copy operation.");
				//Hashes don't match, but since we pass in null in the copy methods this means
				// the file already exists at its destination. Job done.
			}


			if((operationsMap & DomainAPI.REMOVE_FROM_LOCAL) > 0) {
				Log.v(TAG, "DomWorker removing file from local. FileUID: " + fileUID);
				domainAPI.removeFileFromLocal(fileUID);
			}
			if((operationsMap & DomainAPI.REMOVE_FROM_SERVER) > 0) {
				Log.v(TAG, "DomWorker removing file from server. FileUID: " + fileUID);
				domainAPI.removeFileFromServer(fileUID);
			}

		}
		//If this fails due to server connection issues, requeue it for later
		catch (ConnectException e) {
			try {
				Log.w(TAG, "DomWorker requeueing due to connection issues!");
				domainAPI.enqueue(fileUID, operationsMap);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return Result.failure();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}
}
