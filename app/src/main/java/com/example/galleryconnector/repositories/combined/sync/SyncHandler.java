package com.example.galleryconnector.repositories.combined.sync;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.PersistedMapQueue;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.combinedtypes.GJournal;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class SyncHandler {
	private static final String TAG = "Gal.SRepo.Sync";

	//TODO Figure out how to persist these two (SharedPreferences? DataStore?)
	private int lastSyncLocalID;
	private int lastSyncServerID;

	private final PersistedMapQueue<UUID, Nullable> pendingSync;

	private final GalleryRepo galleryRepo;
	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;



	public static SyncHandler getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final SyncHandler INSTANCE = new SyncHandler();
	}
	private SyncHandler() {
		galleryRepo = GalleryRepo.getInstance();
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();


		lastSyncLocalID = 30;
		lastSyncServerID = 2;


		String appDataDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		Path persistLocation = Paths.get(appDataDir, "queues", "syncQueue.txt");

		pendingSync = new PersistedMapQueue<UUID, Nullable>(persistLocation) {
			@Override
			public UUID parseKey(String keyString) { return UUID.fromString(keyString); }
			@Override
			public Nullable parseVal(String valString) { return null; }
		};


		//Catch up on synchronizations we've missed while the app has been closed
		//Will only run once since this class is a singleton
		catchUpOnSyncing();
	}


	//---------------------------------------------------------------------------------------------


	//Actually launches n workers to execute the next n sync operations (if available)
	public void doSomething(int times) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Get the next N fileUIDs that need syncing
		List<Map.Entry<UUID, Nullable>> filesToSync = pendingSync.pop(times);

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

	//---------------------------------------------------------------------------------------------

	//TODO We don't actually update or store these yet. Right now they're always 0 and do nothing.
	//TODO Also, this doesn't exactly work with multiple accounts atm
	public void updateLastSyncLocal(int id) {
		if(id > lastSyncLocalID)	//Gets rid of race conditions when several file updates come in at once. We just want the largest ID.
			lastSyncLocalID = id;
	}
	public void updateLastSyncServer(int id) {
		if(id > lastSyncServerID)
			lastSyncServerID = id;
	}

	public int getLastSyncLocal() {
		return lastSyncLocalID;
	}
	public int getLastSyncServer() {
		return lastSyncServerID;
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------

	//Bro HOW do we do this correctly? How do other people even do this correctly? Google?
	//We can sync all of server to local np, but then syncing local to server is a pain
	//What if server file is being updated, how do we get a word in.
	//Realistically I can get away with a shitty implementation, but like wth.


	/*
	Note: This sync is shitty af. It works for our current purposes, but will need to be revised.
	Right now, we disregard currently updating files when syncing l->s and s->l.
	A possible solution is requiring a last hash when updating to be sure there were no very new changes.
	If last has does not match, then require client to merge and then try syncing again.
	Also, our merge rn is just last writer wins.
	*/

	//TODO If fail, add back to queue. Need to do some exception testing before that though.
	//Returns true if data was written, false if not
	public boolean trySync(UUID fileUID) throws ExecutionException, InterruptedException, IOException {
		Log.i(TAG, String.format("SYNC TO SERVER called with fileUID='%s'", fileUID));

		//Get props
		//Check hashes
		//Get contents
		//Merge
		//Write with hash included



		List<LJournal> localJournals = localRepo.database.getJournalDao().loadAllByFileUID(fileUID);
		List<SJournal> serverJournals = serverRepo.getJournalEntriesForFile(fileUID);

		//If the file is missing from one or both repos, there is nothing to sync
		if(localJournals.isEmpty() || serverJournals.isEmpty())
			return false;



		LJournal latestLocalJournal = localJournals.get(localJournals.size()-1);
		SJournal latestServerJournal = serverJournals.get(serverJournals.size()-1);

		//If the latest hashes of both files match, nothing needs to be synced
		if(Objects.equals(latestLocalJournal.attrhash, latestServerJournal.attrhash))
			return false;

		//If attrHash doesn't match, but fileHash does, the file contents are the same but other props are different
		else if(Objects.equals(latestLocalJournal.filehash, latestServerJournal.filehash)) {
			//We're just going with a last writer wins for now TODO Upgrade this later

			//TODO Add IllegalState errors
			//Write whichever properties are most recent to their opposite repository
			Instant localInstant = Instant.ofEpochMilli(latestLocalJournal.changetime);
			Instant serverInstant = Instant.ofEpochMilli(latestServerJournal.changetime);
			if(localInstant.isAfter(serverInstant)) {										//If local is more recent...
				LFile localChanges = localRepo.getFileProps(fileUID);						//Get changes from local
				assert localChanges != null;

				galleryRepo.putFilePropsServer(GFile.fromLocalFile(localChanges));			//And upload them to the server
			}
			else {																			//Else, if server is more recent...
				SFile serverChanges = serverRepo.getFileProps(fileUID);						//Get changes from server
				assert serverChanges != null;

				galleryRepo.putFilePropsLocal(GFile.fromServerFile(serverChanges));			//And upload them to local
			}

			//Assuming the update was successful, we're done here
			return true;
		}

		//--------------------------------------------------

		//If we are here, that means the fileHashes differ
		//If one repo is simply out of date, we don't need to merge and can just send over the updates
		//If both repos have changes, we unfortunately need to merge

		//To determine how we need to sync, we need to find the last spot these files were synced
		//TODO Naive LCD implementation O(n^2). Improve later with a map and some fancy comparing.
		int localIndex = -1;
		int serverIndex = -1;
		for(int i = localJournals.size()-1; i >= 0; i--) {
			LJournal lJournal = localJournals.get(i);

			for(int j = serverJournals.size()-1; j >= 0; j--) {
				SJournal sJournal = serverJournals.get(j);

				if(entriesMatch(lJournal, sJournal)) {
					localIndex = i;
					serverIndex = j;
					break;
				}
			}
			if(localIndex != -1)
				break;
		}

		if(localIndex == -1 /*|| serverIndex == -1*/)
			throw new RuntimeException("No matching entries found for sync!");


		boolean localHasChanges = localIndex != localJournals.size()-1;
		boolean serverHasChanges = serverIndex != serverJournals.size()-1;



		//If both repos have changes, we need to merge...
		if(localHasChanges && serverHasChanges) {
			//I cannot be bothered, so I'm just going to do last writer wins for merging 	TODO Upgrade this
			//Just modify the booleans to do the right copy below

			Instant localInstant = Instant.ofEpochMilli(latestLocalJournal.changetime);
			Instant serverInstant = Instant.ofEpochMilli(latestServerJournal.changetime);

			//If the localRepo was updated most recently...
			if(localInstant.isAfter(serverInstant))
				serverHasChanges = false;		//Pretend the serverRepo doesn't have changes so that they're overwritten
			else
				localHasChanges = false;		//Pretend the localRepo doesn't have changes so that they're overwritten

		}



		//TODO Test that removing a block from the repo mid-copy is handled

		//If local is the only repo with changes...
		if(localHasChanges && !serverHasChanges) {
			//Get the file properties from the local database
			LFile file = localRepo.getFileProps(fileUID);
			if(file == null) throw new FileNotFoundException("File not found locally! fileuid="+fileUID);

			//Get the blockset of the file
			List<String> blockset = file.fileblocks;
			//And send them all to the server
			domainAPI.copyBlocksToServer(blockset);

			//Now that the blockset is uploaded, put the file metadata to the server database
			galleryRepo.putFilePropsServer(GFile.fromLocalFile(file), latestServerJournal.filehash, latestServerJournal.attrhash);
		}

		//Else, if server is the only repo with changes...
		else if(!localHasChanges && serverHasChanges) {
			//Get the file properties from the server database
			SFile file = serverRepo.getFileProps(fileUID);
			if(file == null) throw new FileNotFoundException("File not found in server! fileuid="+fileUID);

			//Get the blockset of the file
			List<String> blockset = file.fileblocks;
			//And download them all to local
			domainAPI.copyBlocksToLocal(blockset);

			//Now that the blockset is uploaded, put the file metadata to the server database
			galleryRepo.putFilePropsLocal(GFile.fromServerFile(file), latestLocalJournal.filehash, latestLocalJournal.attrhash);
		}




		/*
		//Now that we have the last matching indices, figure out what to do to get things in sync

		//If the first two items are matching, there's nothing to sync
		if(localIndex == localJournals.size()-1 && serverIndex == serverJournals.size()-1) {
			//No data has been written, return false
			return false;
		}
		//In this case, only the server has updates, so we need to sync to local
		else if(localIndex == localJournals.size()-1 && serverIndex < serverJournals.size()-1) {
			domainAPI.copyFileToLocal(fileUID);
			//Once we start sending prevHash, if this returns as a fail send it to merge
		}
		//In this case, only the local has updates, so we need to sync to server
		else if(localIndex < localJournals.size()-1 && serverIndex == serverJournals.size()-1) {
			domainAPI.copyFileToServer(fileUID);
			//Once we start sending prevHash, if this returns as a fail send it to merge
		}
		//Otherwise, both have updates and we need to merge
		else {
			LJournal local = localJournals.get(localIndex);
			SJournal server = serverJournals.get(serverIndex);

			merge(local, server);
		}
		 */


		//Data has been written, notify any observers and return true
		galleryRepo.notify();
		return true;
	}


	//Merging is going to take a significant amount of effort, so for now we're doing last writer wins.
	//Maybe we should just always copy from Server. Idk.
	public void merge(LJournal local, SJournal server) throws IOException {
		//TODO Don't know if these date conversions work from the different sql
		// Might need to convert to epoch during sql gets
		Instant localInstant = Instant.ofEpochMilli(local.changetime);
		Instant serverInstant = Instant.ofEpochMilli(server.changetime);

		if(localInstant.isAfter(serverInstant))
			domainAPI.copyFileToServer(local.fileuid);
		else
			domainAPI.copyFileToLocal(server.fileuid);
	}


	private boolean entriesMatch(LJournal local, SJournal server) {
		GJournal localJournal = new Gson().fromJson(local.toJson(), GJournal.class);
		GJournal serverJournal = new Gson().fromJson(server.toJson(), GJournal.class);
		return localJournal.equals(serverJournal);
	}


	//----------------------------------------------

	//TODO Do we want this to be account specific? Probably not, may as well just sync everything we've got.
	public void catchUpOnSyncing() {
		Thread thread = new Thread(() -> {
			//Get all new journal entries we've missed
			List<LJournal> localJournals = localRepo.database.getJournalDao().loadAllAfterID(lastSyncLocalID);
			List<SJournal> serverJournals;
			try {
				serverJournals = serverRepo.getJournalEntriesAfter(lastSyncServerID);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}


			//We just want the fileUIDs of the new journal entries
			HashSet<UUID> fileUIDs = new HashSet<>();

			for(LJournal journal : localJournals) {
				if(journal == null) continue;
				fileUIDs.add(journal.fileuid);
			}
			for(SJournal journal : serverJournals) {
				if(journal == null) continue;
				UUID uuid = journal.fileuid;
				fileUIDs.add(uuid);
			}


			//Queue all fileUIDs for sync
			List<UUID> fileUIDsList = new ArrayList<>(fileUIDs);
			enqueue(fileUIDsList);
		});
		thread.start();
	}
}
