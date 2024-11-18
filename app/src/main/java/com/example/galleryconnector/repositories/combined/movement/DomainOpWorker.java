package com.example.galleryconnector.repositories.combined.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


public class DomainOpWorker extends Worker {
	private static final String TAG = "Gal.DOp";
	private final DomainAPI domainAPI;


	public DomainOpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
	}


	//TODO Make sure this reruns if no internet

	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "DomainOpWorker doing work");

		//If this work should be skipped (see skipConflict(), just return success and don't do any actual work)
		if(getTags().contains("SKIPPED"))
			return Result.success();


		String operationString = getInputData().getString("OPERATION");
		assert operationString != null;
		DomainAPI.Operation operation = DomainAPI.Operation.valueOf(operationString);

		String fileUIDString = getInputData().getString("FILEUID");
		assert fileUIDString != null;
		UUID fileUID = UUID.fromString(fileUIDString);



		//Look for any conflicting operation and, if found, skip it AND the current operation.
		//E.g. This Worker is COPY_TO_LOCAL, but there is a later worker designated REMOVE_FROM_LOCAL.
		//Copying a file to local just to remove it would be redundant, so we can safely cancel both.
		try {
			boolean conflict = skipConflict(operation, fileUID);
			if(conflict) return Result.success();

		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}


		try {
			switch (operation) {
				case COPY_TO_LOCAL: domainAPI.copyFileToLocal(fileUID);
					break;
				case REMOVE_FROM_LOCAL: domainAPI.removeFileFromLocal(fileUID);
					break;
				case COPY_TO_SERVER: domainAPI.copyFileToServer(fileUID);
					break;
				case REMOVE_FROM_SERVER: domainAPI.removeFileFromServer(fileUID);
					break;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		return Result.success();
	}



	//Returns true if a conflicting operation was found and skipped
	private boolean skipConflict(DomainAPI.Operation operation, UUID fileUID)
									throws ExecutionException, InterruptedException {
		DomainAPI.Operation oppositeOperation = operation.getOpposite();


		//Look for any other domain operations enqueued for this file
		WorkQuery workQuery = WorkQuery.Builder
				.fromTags(Collections.singletonList(fileUID.toString()))
				.addStates(Collections.singletonList(WorkInfo.State.ENQUEUED))
				.build();

		WorkManager workManager = WorkManager.getInstance(getApplicationContext());
		List<WorkInfo> workInfos = workManager.getWorkInfos(workQuery).get();

		//Filter for only workers with the tag of the conflicting/opposite operation
		//Can't do this in the workQuery because the tags would be ORed rather than ANDed like we need
		workInfos = workInfos.stream()
				.filter(workInfo -> workInfo.getTags().contains(oppositeOperation.toString()) &&
									!workInfo.getTags().contains("SKIPPED"))
				.collect(Collectors.toList());


		//If a conflicting operation exists, skip it. Skip only the first in line if there happen to be multiple.
		if(!workInfos.isEmpty()) {
			Log.i(TAG, "Found a conflicting operation for "+operation+", cancelling...");
			WorkInfo workInfo = workInfos.get(0);

			//Replace the conflicting worker with this one, adding a SKIPPED tag
			OneTimeWorkRequest.Builder builder = domainAPI.buildWorker(fileUID, operation);
			builder.setId(workInfo.getId());
			builder.addTag("SKIPPED");
			WorkRequest updatedRequest = builder.build();

			workManager.updateWork(updatedRequest);
			return true;
		}

		return false;
	}
}
