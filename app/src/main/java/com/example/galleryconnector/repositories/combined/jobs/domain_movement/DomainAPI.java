package com.example.galleryconnector.repositories.combined.jobs.domain_movement;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.sync.SyncWorker;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.content.LContent;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SContent;
import com.example.galleryconnector.repositories.server.servertypes.SFile;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class DomainAPI {
	private static final String TAG = "Gal.DomAPI";
	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;


	public static DomainAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final DomainAPI INSTANCE = new DomainAPI();
	}
	private DomainAPI() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();
	}
	


	public static final int COPY_TO_LOCAL = 1;
	public static final int REMOVE_FROM_LOCAL = 2;
	public static final int COPY_TO_SERVER = 4;
	public static final int REMOVE_FROM_SERVER = 8;
	public static final int LOCAL_MASK = COPY_TO_LOCAL | REMOVE_FROM_LOCAL;
	public static final int SERVER_MASK = COPY_TO_SERVER | REMOVE_FROM_SERVER;
	public static final int MASK = LOCAL_MASK | SERVER_MASK;



	//TODO Enqueue the worker with an initial delay of a few seconds, to account for user moving back and forth in-app
	// Only on the first queue though, if the worker doesn't already exist
	// Or maybe for every one? Actually yeah probably that

	//TODO We also need to make sure the worker is listening for cancel operations, though not sure we should actually
	// do that, as if a move op is enqueued, canceling after a copy and before the remove would be a no-no

	//Enqueue a Worker to facilitate the domain operations
	//Returns the operation for testing purposes
	public void enqueue(@NonNull UUID fileUID, @NonNull Integer... newOperations) {
		//Grab any existing operations for an already scheduled worker
		int operationsMask = getExistingOperations(fileUID);
		Log.d(TAG, "Existing operations="+operationsMask+". fileUID='"+fileUID+"'");

		//Add all new operations to the existing mask
		for(int operation : newOperations) {
			operationsMask |= operation;
		}


		//Look for any conflicting operations and, if found, remove both.
		//E.g. If this operations mask now contains both COPY_TO_LOCAL and REMOVE_FROM_LOCAL,
		// we can safely remove both as copying a file to local just to remove it would be redundant
		if((operationsMask & LOCAL_MASK) == LOCAL_MASK) {
			Log.d(TAG, "Operation conflict, removing local ops! fileUID='"+fileUID+"'");
			operationsMask &= ~(LOCAL_MASK);
		}
		else if((operationsMask & SERVER_MASK) == SERVER_MASK) {
			Log.d(TAG, "Operation conflict, removing server ops! fileUID='"+fileUID+"'");
			operationsMask &= ~(SERVER_MASK);
		}


		//Queue a DomainWorker with the new operations mask, replacing any existing worker
		OneTimeWorkRequest worker = buildWorker(fileUID, operationsMask).build();
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
		workManager.enqueueUniqueWork("domain_"+fileUID, ExistingWorkPolicy.REPLACE, worker);
	}


	//Dequeue certain operations from an existing worker.
	//Returns the operation for testing purposes
	@Nullable
	public void dequeue(@NonNull UUID fileUID, @NonNull Integer... operations) {
		//Grab any existing operations for an already scheduled worker
		int operationsMask = getExistingOperations(fileUID);

		//If there are no operations queued, we're done here
		if((operationsMask & MASK) == 0)
			return;


		//Starting from the existing operations mask, remove all specified operations
		for(int operation : operations) {
			operationsMask &= ~operation;
		}


		//Queue a DomainWorker with the new operations mask, replacing any existing worker
		OneTimeWorkRequest worker = buildWorker(fileUID, operationsMask).build();
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
		workManager.enqueueUniqueWork("domain_"+fileUID, ExistingWorkPolicy.REPLACE, worker);
	}



	//Returns the operation for testing purposes
	private int getExistingOperations(@NonNull UUID fileUID) {
		try {
			//Grab any existing workers for this fileUID
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			WorkQuery workQuery = WorkQuery.Builder
					.fromUniqueWorkNames(Collections.singletonList("domain_" + fileUID))
					.addStates(Collections.singletonList(WorkInfo.State.ENQUEUED))
					.build();
			List<WorkInfo> existingInfo = workManager.getWorkInfos(workQuery).get();


			//If this fileUID has no workers, we have no existing operations
			if(existingInfo.isEmpty())
				return 0;


			//Since we schedule unique work, there will only be one worker. Grab it and get its tags
			Set<String> tags = existingInfo.get(0).getTags();

			//Look for the "OPERATIONS_..." tag, which will contain the existing operations mask
			for(String tag : tags) {
				if(tag.startsWith("OPERATIONS_")) {
					String existingOperations = tag.substring("OPERATIONS_".length());
					return Integer.parseInt(existingOperations);
				}
			}
			return 0;
		}
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }
	}



	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileUID, int operationsMask) {
		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileUID.toString());
		data.putString("OPERATIONS", Integer.toString(operationsMask));

		Constraints.Builder constraints = new Constraints.Builder();

		//If we are uploading/downloading (potentially large) data, require an unmetered connection
		if((operationsMask & (COPY_TO_LOCAL | COPY_TO_SERVER)) != 0)
				constraints.setRequiredNetworkType(NetworkType.UNMETERED);

		//If we're copying (potentially large) data onto device, require storage not to be low
		if((operationsMask & COPY_TO_LOCAL) != 0)
			constraints.setRequiresStorageNotLow(true);

		return new OneTimeWorkRequest.Builder(DomainOpWorker.class)
				.setConstraints(constraints.build())
				.addTag(fileUID.toString()).addTag("DOMAIN")
				.addTag("OPERATIONS_"+operationsMask)
				.setInputData(data.build());
	}


	//=============================================================================================
	// API
	//=============================================================================================

	public SFile createFileOnServer(@NonNull LFile file) throws IllegalStateException, IOException {
		return copyFileToServer(file, "null", "null");
	}
	public SFile copyFileToServer(@NonNull LFile file, String lastServerFileHash, String lastServerAttrHash)
			throws IllegalStateException, IOException {
		//Get the hash of the file
		String fileHash = file.filehash;

		//If it exists, copy that content to the server
		if(fileHash != null)
			copyContentToServer(fileHash);


		//Now that the content is uploaded, create/update the file metadata
		SFile fileProps = GFile.fromLocalFile(file).toServerFile();

		//And attempt to put the changes to the server
		try {
			fileProps = serverRepo.putFileProps(fileProps, lastServerFileHash, lastServerAttrHash);
		} catch (ContentsNotFoundException e) {
			Log.e(TAG, "copyFileToServer() failed! Content failed to copy!");
			throw new RuntimeException(e);
		}


		//If this method was called with "null" hashes (not actually null),
		// it was used to try to create a file for the first time
		if(Objects.equals(lastServerFileHash, "null") && Objects.equals(lastServerAttrHash, "null")) {
			//If no illegalStateException was thrown, that means the file was just CREATED in server.
			//It is now the latest sync point
			localRepo.putLastSyncedData(GFile.fromServerFile(fileProps).toLocalFile());
		}

		return fileProps;
	}


	public LFile createFileOnLocal(@NonNull SFile file) throws IllegalStateException, IOException {
		return copyFileToLocal(file, "null", "null");
	}
	public LFile copyFileToLocal(@NonNull SFile file, String lastLocalFileHash, String lastLocalAttrHash)
			throws IllegalStateException, IOException {
		//Get the hash of the file
		String fileHash = file.filehash;

		//If it exists, copy that content to local
		if(fileHash != null)
			copyContentsToLocal(fileHash);


		//Now that the content is downloaded, create/update the file metadata
		LFile fileProps = GFile.fromServerFile(file).toLocalFile();

		//And attempt to put the changes to local
		try {
			fileProps = localRepo.putFileProps(fileProps, lastLocalFileHash, lastLocalAttrHash);
		} catch (ContentsNotFoundException e) {
			Log.e(TAG, "copyFileToLocal() failed! Content failed to copy!");
			throw new RuntimeException(e);
		}


		//If this method was called with "null" hashes (not actually null),
		// it was used to try to create a file for the first time
		if(Objects.equals(lastLocalFileHash, "null") && Objects.equals(lastLocalAttrHash, "null")) {
			//If no illegalStateException was thrown, that means the file was just CREATED in server.
			//It is now the latest sync point
			localRepo.putLastSyncedData(fileProps);
		}

		return fileProps;
	}


	//---------------------------------------------------


	@Nullable
	public LContent copyContentsToLocal(@Nullable String fileHash) throws IOException {
		if(fileHash == null) return null;

		try {
			//Check if the content already exists on local
			return localRepo.contentHandler.getProps(fileHash);
		}
		catch (ContentsNotFoundException e) {
			//If it does not, download it
			Uri serverContentUri = serverRepo.getContentDownloadUri(fileHash);
			return localRepo.writeContents(fileHash, serverContentUri);
		}
	}

	public SContent copyContentToServer(@Nullable String fileHash) throws IOException {
		if(fileHash == null) return null;

		try {
			//Check if the content already exists on the server
			return serverRepo.contentConn.getProps(fileHash);
		}
		catch (ContentsNotFoundException e) {
			//If it does not, upload it
			Uri localContentUri = localRepo.getContentUri(fileHash);
			File localFile = new File(localContentUri.toString());

			//Returns filesize
			return serverRepo.uploadData(fileHash, localFile);
		}
	}


	//---------------------------------------------------


	public boolean removeFileFromLocal(@NonNull UUID fileuid) {
		localRepo.deleteFileProps(fileuid);
		localRepo.deleteLastSyncedData(fileuid);
		return true;
	}

	public boolean removeFileFromServer(@NonNull UUID fileuid) throws ConnectException {
		serverRepo.deleteFileProps(fileuid);
		localRepo.deleteLastSyncedData(fileuid);
		return true;
	}
}
