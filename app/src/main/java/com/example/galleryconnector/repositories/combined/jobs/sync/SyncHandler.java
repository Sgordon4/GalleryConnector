package com.example.galleryconnector.repositories.combined.jobs.sync;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.jobs.PersistedMapQueue;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.domain_movement.DomainAPI;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class SyncHandler {
	private static final String TAG = "Gal.SRepo.Sync";

	private final SharedPreferences sharedPrefs;
	private int lastSyncLocalID;
	private int lastSyncServerID;

	private final PersistedMapQueue<UUID, Nullable> pendingSync;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;

	private static final boolean debug = false;



	public static SyncHandler getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final SyncHandler INSTANCE = new SyncHandler();
	}
	private SyncHandler() {
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();


		sharedPrefs = MyApplication.getAppContext()
				.getSharedPreferences("gallery.syncPointers", Context.MODE_PRIVATE);
		lastSyncLocalID = sharedPrefs.getInt("lastSyncLocal", 0);
		lastSyncServerID = sharedPrefs.getInt("lastSyncServer", 0);

		if(debug) Log.d(TAG, "SyncHandler created -----------------------------------");
		if(debug) Log.d(TAG, "Sync pointers:");
		if(debug) Log.d(TAG, "lastSyncLocalID: "+lastSyncLocalID);
		if(debug) Log.d(TAG, "lastSyncServerID: "+lastSyncServerID);


		String appDataDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		Path persistLocation = Paths.get(appDataDir, "queues", "syncQueue.txt");

		pendingSync = new PersistedMapQueue<UUID, Nullable>(persistLocation) {
			@Override
			public UUID parseKey(String keyString) { return UUID.fromString(keyString); }
			@Override
			public Nullable parseVal(String valString) { return null; }
		};
	}




	//---------------------------------------------------------------------------------------------


	//Actually launches n workers to execute the next n sync operations (if available)
	public void doSomething(int times) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Get the next N fileUIDs that need syncing
		List<Map.Entry<UUID, Nullable>> filesToSync = pendingSync.pop(times);
		Log.i(TAG, "SyncHandler doing something "+filesToSync.size()+" times...");


		//For each item...
		for(Map.Entry<UUID, Nullable> entry : filesToSync) {
			//Launch the worker to perform the sync
			WorkRequest request = buildWorker(entry.getKey()).build();
			workManager.enqueue(request);
		}
	}


	public OneTimeWorkRequest.Builder buildWorker(@NonNull UUID fileuid) {
		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileuid.toString());

		return new OneTimeWorkRequest.Builder(SyncWorker.class)
				.setInputData(data.build())
				.addTag(fileuid.toString());
	}


	//---------------------------------------------------------------------------------------------

	public void enqueue(@NonNull UUID fileuid) {
		pendingSync.enqueue(fileuid, null);
	}
	public void enqueue(@NonNull List<UUID> fileuids) {
		Map<UUID, Nullable> map = new LinkedHashMap<>();
		fileuids.forEach(fileUID -> map.put(fileUID, null));
		pendingSync.enqueue(map);
	}

	public void dequeue(@NonNull UUID fileuid) {
		pendingSync.dequeue(fileuid);
	}
	public void dequeue(@NonNull List<UUID> fileuids) {
		pendingSync.dequeue(fileuids);
	}

	public void clearQueuedItems() {
		pendingSync.clear();
	}

	public int getQueueSize() {
		return pendingSync.size();
	}


	//---------------------------------------------------------------------------------------------

	//TODO This is not set up to work with multiple accounts atm. Need to store it as a Map(UUID, Integer).
	public void updateLastSyncLocal(int id) {
		//Gets rid of race conditions when several file updates come in at once. We just want the largest ID.
		if(id > lastSyncLocalID)
			lastSyncLocalID = id;

		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("lastSyncLocal", lastSyncLocalID);
		editor.apply();
	}
	public void updateLastSyncServer(int id) {
		if(id > lastSyncServerID)
			lastSyncServerID = id;

		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("lastSyncServer", lastSyncServerID);
		editor.apply();
	}

	public int getLastSyncLocal() {
		return lastSyncLocalID;
	}
	public int getLastSyncServer() {
		return lastSyncServerID;
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	public GFile trySync(UUID fileUID) throws ConnectException, IllegalStateException {
		Log.i(TAG, String.format("TRY SYNC called with fileUID='%s'", fileUID));

		try {
			LFile localFile = localRepo.getFileProps(fileUID);
			SFile serverFile = serverRepo.getFileProps(fileUID);

			//If the latest hashes of both files match, nothing needs to be synced
			if(Objects.equals(localFile.filehash, serverFile.filehash) &&
				Objects.equals(localFile.attrhash, serverFile.attrhash)) {
				if(debug) Log.d(TAG, "File hashes match, no sync needed. FileUID: "+fileUID);
				return null;
			}



			//If one or both of the hashes don't match, we need the last sync point to determine what to do next
			LFile lastSynced = localRepo.getLastSyncedData(fileUID);
			GFile syncReference;

			//If there is no last sync point stored, we're just going to set things up to do last-writer-wins
			if(lastSynced == null) {
				if(localFile.changetime > serverFile.changetime) {
					Log.w(TAG, "No sync point stored, pretending server is synced");
					syncReference = GFile.fromServerFile(serverFile);
				}
				else {
					Log.w(TAG, "No sync point stored, pretending local is synced");
					syncReference = GFile.fromLocalFile(localFile);
				}
			}
			else {
				if(debug) Log.i(TAG, "Sync point retrieved");
				syncReference = GFile.fromLocalFile(lastSynced);
			}



			//Try to merge file contents and user attributes
			mergeContents(localFile, serverFile, syncReference);
			mergeAttributes(localFile, serverFile, syncReference);


			//Note: If the first update goes through, but the second update fails, we have un problemita

			//If local needs updating...
			if(!Objects.equals(localFile.filehash, syncReference.filehash) ||
				!Objects.equals(localFile.attrhash, syncReference.attrhash)) {
				if(debug) Log.d(TAG, "Local needs updating");
				localRepo.putFileProps(syncReference.toLocalFile(), localFile.filehash, localFile.attrhash);
			}
			//If server needs updating...
			if(!Objects.equals(serverFile.filehash, syncReference.filehash) ||
				!Objects.equals(serverFile.attrhash, syncReference.attrhash)) {
				if(debug) Log.d(TAG, "Server needs updating");
				serverRepo.putFileProps(syncReference.toServerFile(), serverFile.filehash, serverFile.attrhash);
			}


			//Since we have a new sync point, update the last sync point table
			if(debug) Log.d(TAG, "Writing sync point");
			localRepo.putLastSyncedData(syncReference.toLocalFile());

			//Data has been written, notify any observers and return true
			GalleryRepo.getInstance().notifyObservers(syncReference);
			return syncReference;
		}
		catch (FileNotFoundException e) {
			//If the file is missing from one or both repos, there is nothing to sync
			if(debug) Log.d(TAG, "At least one of the repos is missing the file, no sync needed. FileUID: "+fileUID);
			return null;
		} catch (ConnectException e) {
			throw e;
		} catch (ContentsNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	private GFile mergeContents(@NonNull LFile local, @NonNull SFile server, @NonNull GFile syncReference) throws IOException {
		boolean localHasFileChanges = !Objects.equals(local.filehash, syncReference.filehash);
		boolean serverHasFileChanges = !Objects.equals(server.filehash, syncReference.filehash);

		//If both repos have file changes, we need to merge. Womp womp.
		if(localHasFileChanges && serverHasFileChanges) {
			Log.i(TAG, "Both repos have file content changes, merging (last writer wins). FileUID: "+local.fileuid);
			//We have what we need to actually merge the files (A, B, and Original),
			// but I cannot be asked so we're doing last writer wins.
			//TODO This needs to be upgraded

			if(local.modifytime > server.modifytime)
				serverHasFileChanges = false;
			else
				localHasFileChanges = false;
		}

		//If only local has file content changes, just copy those to server
		if(localHasFileChanges && !serverHasFileChanges) {
			if(debug) Log.d(TAG, "Only local has file content changes, copying content to server. FileUID: "+local.fileuid);
			domainAPI.copyContentToServer(local.filehash);
			syncReference.filesize = local.filesize;
			syncReference.filehash = local.filehash;
			syncReference.changetime = local.changetime;
			syncReference.modifytime = local.modifytime;
		}
		//If only server has file content changes, just copy those to local
		else if(!localHasFileChanges && serverHasFileChanges) {
			if(debug) Log.d(TAG, "Only server has file content changes, copying content to local. FileUID: "+server.fileuid);
			domainAPI.copyContentsToLocal(server.filehash);
			syncReference.filesize = server.filesize;
			syncReference.filehash = server.filehash;
			syncReference.changetime = server.changetime;
			syncReference.modifytime = server.modifytime;
		}

		return syncReference;
	}


	private GFile mergeAttributes(@NonNull LFile local, @NonNull SFile server, @NonNull GFile syncReference) {
		boolean localHasAttrChanges = !Objects.equals(local.attrhash, syncReference.attrhash);
		boolean serverHasAttrChanges = !Objects.equals(server.attrhash, syncReference.attrhash);

		//If both repos have user attribute changes, we need to merge. Womp womp.
		if(localHasAttrChanges && serverHasAttrChanges) {
			Log.i(TAG, "Both repos have user attribute changes, merging (last writer wins). FileUID: "+local.fileuid);
			//We have what we need to actually merge the attributes (A, B, and Original),
			// but I cannot be asked so we're doing last writer wins.
			//TODO This needs to be upgraded

			if(local.changetime > server.changetime)
				serverHasAttrChanges = false;
			else
				localHasAttrChanges = false;
		}

		//If only local has user attribute changes, just copy those
		if(localHasAttrChanges && !serverHasAttrChanges) {
			if(debug) Log.d(TAG, "Only local has user attribute changes. FileUID: "+local.fileuid);
			syncReference.userattr = local.userattr;
			syncReference.attrhash = local.attrhash;
			syncReference.changetime = local.changetime;
		}
		//If only server has user attribute changes, just copy those
		else if(!localHasAttrChanges && serverHasAttrChanges) {
			if(debug) Log.d(TAG, "Only server has user attribute changes. FileUID: "+server.fileuid);
			syncReference.userattr = server.userattr;
			syncReference.attrhash = server.attrhash;
			syncReference.changetime = server.changetime;
		}

		return syncReference;
	}


	//---------------------------------------------------------------------------------------------

	//TODO This should provide an account when grabbing Server-side journals
	//Catch up on synchronizations we've missed while the app has been closed
	public void catchUpOnSyncing() {
		Thread thread = new Thread(() -> {
			//Get all new journal entries we've missed
			List<LJournal> localJournals = localRepo.database.getJournalDao().loadAllAfterID(lastSyncLocalID);
			List<SJournal> serverJournals;
			try {
				serverJournals = serverRepo.getJournalEntriesAfter(lastSyncServerID);
			} catch (ConnectException e) {
				return;
			}


			//We just want the fileUIDs of the new journal entries
			HashSet<UUID> fileUIDs = new HashSet<>();

			int maxLocalID = lastSyncLocalID;
			for(LJournal journal : localJournals) {
				if(journal == null) continue;
				fileUIDs.add(journal.fileuid);

				maxLocalID = Math.max(maxLocalID, journal.journalid);
			}
			int maxServerID = lastSyncServerID;
			for(SJournal journal : serverJournals) {
				if(journal == null) continue;
				fileUIDs.add(journal.fileuid);

				maxServerID = Math.max(maxServerID, journal.journalid);
			}

			Log.i(TAG, "SyncHandler catchup found "+fileUIDs.size()+" new files to sync.");

			//Queue all fileUIDs for sync
			List<UUID> fileUIDsList = new ArrayList<>(fileUIDs);
			enqueue(fileUIDsList);


			updateLastSyncLocal(maxLocalID);
			updateLastSyncServer(maxServerID);

		});
		thread.start();
	}
}
