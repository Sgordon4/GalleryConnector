package com.example.galleryconnector.sync;

import android.util.Log;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.local.journal.LJournalEntity;
import com.example.galleryconnector.movement.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class SyncHandler {
	private static final String TAG = "Gal.SRepo.Sync";

	private int lastSyncLocalID;
	private int lastSyncServerID;

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
		lastSyncLocalID = 0;
		lastSyncServerID = 0;

		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
	}

	//TODO We don't actually update or store these yet. Right now they're always 0 and do nothing.
	public void updateLastSyncLocal(int id) {
		if(id > lastSyncLocalID)	//Gets rid of race conditions when several file updates come in at once. We just want the largest ID.
			lastSyncLocalID = id;
	}
	public void updateLastSyncServer(int id) {
		if(id > lastSyncServerID)
			lastSyncServerID = id;
	}




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

	//Returns true if data was written, false if not
	public boolean trySync(UUID fileUID) throws ExecutionException, InterruptedException, IOException {
		Log.i(TAG, String.format("SYNC TO SERVER called with fileUID='%s'", fileUID));

		List<LJournalEntity> localJournals = localRepo.database.getJournalDao().loadAllByFileUID(fileUID);
		List<JsonObject> serverJournals = serverRepo.getJournalEntriesForFile(fileUID);

		//if(localJournals.isEmpty() && serverJournals.isEmpty())
		//	throw new FileNotFoundException("File not found in local or server! FileUID='"+fileUID+"'");

		//If the file is missing from one or both repos, there is nothing to sync
		if(localJournals.isEmpty() || serverJournals.isEmpty())
			return false;



		//To determine how we need to sync, we need to find the last spot these files were synced
		//TODO Naive LCD implementation O(n^2). Improve later with a map and some fancy comparing.
		int localIndex = -1;
		int serverIndex = -1;
		for(int i = localJournals.size()-1; i >= 0; i--) {
			LJournalEntity lJournal = localJournals.get(i);

			for(int j = serverJournals.size()-1; j >= 0; j--) {
				JsonObject sJournal = serverJournals.get(j);

				if(entriesMatch(lJournal, sJournal)) {
					localIndex = i;
					serverIndex = j;
					break;
				}
			}
			if(localIndex != -1)
				break;
		}

		if(localIndex == -1 || serverIndex == -1)
			throw new RuntimeException("No matching entries found for sync!");



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
			LJournalEntity local = localJournals.get(localIndex);
			JsonObject server = serverJournals.get(serverIndex);

			merge(local, server);
		}


		//Data has been written, return true
		return true;
	}


	//Merging is going to take a significant amount of effort, so for now we're doing last writer wins.
	//Maybe we should just always copy from Server. Idk.
	public void merge(LJournalEntity local, JsonObject server) throws IOException {
		//TODO Don't know if these date conversions work from the different sql
		// Might need to convert to epoch during sql gets
		Date localDate = new Date(local.changetime);
		Date serverDate = new Date(server.get("changetime").getAsLong());
		if(localDate.after(serverDate))
			domainAPI.copyFileToServer(local.fileuid);
		else
			domainAPI.copyFileToLocal(UUID.fromString(server.get("fileuid").getAsString()));
	}


	private boolean entriesMatch(LJournalEntity local, JsonObject server) {
		LJournalEntity serverFile = new Gson().fromJson(server, LJournalEntity.class);
		return local.equals(serverFile);
	}


	//----------------------------------------------

	public void trySyncAll() throws ExecutionException, InterruptedException, IOException {
		//Get all new journal entries
		List<LJournalEntity> localJournals = localRepo.database.getJournalDao().loadAllAfterID(lastSyncLocalID);
		List<JsonObject> serverJournals = serverRepo.getJournalEntriesAfter(lastSyncServerID);


		//We just want the fileUIDs of the new journal entries
		HashSet<UUID> fileUIDs = new HashSet<>();

		for(LJournalEntity journal : localJournals) {
			if(journal == null) continue;
			fileUIDs.add(journal.fileuid);
		}
		for(JsonObject journal : serverJournals) {
			if(journal == null) continue;
			UUID uuid = UUID.fromString(journal.get("fileuid").getAsString());
			fileUIDs.add(uuid);
		}


		//For each fileUID, try to sync
		for(UUID fileUID : fileUIDs) {
			trySync(fileUID);
		}
	}
}
