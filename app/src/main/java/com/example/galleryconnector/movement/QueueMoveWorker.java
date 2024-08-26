package com.example.galleryconnector.movement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.UUID;

public class QueueMoveWorker extends Worker {
	private static final String TAG = "Gal.QMove";
	private final DomainAPI domainAPI;


	public QueueMoveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		domainAPI = DomainAPI.getInstance();
	}


	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "QueueMoveWorker doing work");

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


		//Queue or remove the operation based on the passed parameters
		if(addingToQueue)
			domainAPI.queueOperation(operation, fileUID);
		else
			domainAPI.removeOperation(operation, fileUID);


		return Result.success();
	}
}




/*
	public Result doWork() {
		Log.i(TAG, "MoveQueueWorker doing work");

		String queueing = getInputData().getString("QUEUEING");			//T/F
		String operationStr = getInputData().getString("OPERATION");	//DomainAPI.Operation
		String file = getInputData().getString("FILEUID");				//UUID


		//Check that we have what we need
		if(queueing == null || operationStr == null || file == null) {
			Log.e(TAG, "MoveQueueWorker called with missing parameters!!");
			return Result.failure();
		}
		if(!queueing.equals("true") && !queueing.equals("false")) {
			Log.e(TAG, "Invalid parameter ADD: '"+queueing+"'");
			return Result.failure();
		}
		if( !operationStr.equals(COPY_TO_LOCAL.toString()) &&
			!operationStr.equals(REMOVE_FROM_LOCAL.toString()) &&
			!operationStr.equals(COPY_TO_SERVER.toString()) &&
			!operationStr.equals(REMOVE_FROM_SERVER.toString())) {
			Log.e(TAG, "Invalid parameter OPERATION: '"+operationStr+"'");
			return Result.failure();
		}

		boolean addingToQueue = Boolean.getBoolean(queueing);
		DomainAPI.Operation operation = DomainAPI.Operation.valueOf(operationStr);
		UUID fileUID = UUID.fromString(file);


		//If we're adding to the queue
		if(addingToQueue) {
			domainAPI.queueOperation(operation, fileUID);
		}
		else
			domainAPI.removeOperation(operation, fileUID);


		return Result.success();
	}
 */