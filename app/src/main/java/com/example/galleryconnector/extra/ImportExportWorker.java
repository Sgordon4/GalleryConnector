package com.example.galleryconnector.extra;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.UUID;

public class ImportExportWorker extends Worker {
	private static final String TAG = "Gal.FIO";

	public ImportExportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
		super(context, params);
	}


	@NonNull
	@Override
	public Result doWork() {
		Log.i(TAG, "FileIOWorker doing work");

		String operation = getInputData().getString("OPERATION");	//Import or export
		String target = getInputData().getString("TARGET_URI");	//IO to/from this URI
		String parent = getInputData().getString("PARENTUID");		//Parentuid of our file

		String account =  getInputData().getString("ACCOUNTUID");	//Accountuid of new file
		String file =  getInputData().getString("FILEUID");		//Fileuid of current file


		//Check that we have what we need
		if(operation == null) {
			Log.e(TAG, "No operation specified!");
			return Result.failure();
		}
		if(!operation.equals("IMPORT") && !operation.equals("EXPORT")) {
			Log.e(TAG, "Invalid operation '"+operation+"'!");
			return Result.failure();
		}
		if(operation.equals("IMPORT") && (target == null || parent == null || account == null)) {
			Log.e(TAG, "Import called with missing parameters!");
			return Result.failure();
		}
		if(operation.equals("EXPORT") && (target == null || parent == null || file == null)) {
			Log.e(TAG, "EXPORT called with missing parameters!");
			return Result.failure();
		}


		//---------------------------------------------


		//Start the operation
		if(Objects.equals(operation, "IMPORT")) {
			Log.i(TAG, "Importing file '"+target+"'");
			UUID accountUID = UUID.fromString(account);
			UUID parentUID = UUID.fromString(parent);
			Uri sourceUri = Uri.parse(target);

			//TODO Import into a temp file and get fileHash from there
			String fileHash = "testImport";

			//Import the file to the local system
			try {
				ImportExportApi.getInstance().importFileToLocal(accountUID, parentUID, fileHash, sourceUri);
			} catch (UnknownHostException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if(Objects.equals(operation, "EXPORT")) {
			Log.i(TAG, "Exporting file '"+file+"'");
			UUID fileUID = UUID.fromString(file);
			UUID parentUID = UUID.fromString(parent);
			Uri destUri = Uri.parse(target);

			//Export the file to the device's files
			throw new RuntimeException("Stub!");
		}


		//TODO Send a domain movement as the data for the result
		return Result.success();
	}
}
