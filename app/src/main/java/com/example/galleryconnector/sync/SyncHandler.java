package com.example.galleryconnector.sync;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.local.journal.LJournalEntity;
import com.example.galleryconnector.movement.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class SyncHandler {

	private int lastSyncLocalID;
	private int lastSyncServerID;

	private final LocalRepo localRepo;
	private final ServerRepo serverRepo;

	private final DomainAPI domainAPI;

	//private final Map<UUID, JsonObject> mapLocal;
	//private final Map<UUID, JsonObject> mapServer;


	public static SyncHandler getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final SyncHandler INSTANCE = new SyncHandler();
	}
	private SyncHandler() {
		lastSyncLocalID = 0;	//TODO Get from app props
		lastSyncServerID = 0;

		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
	}

	private void updateLastSyncLocal(int id) {
		lastSyncLocalID = id;
	}
	private void updateLastSyncServer(int id) {
		lastSyncServerID = id;
	}




	//Bro HOW do we do this correctly? How do other people even do this correctly? Google?
	//We can sync all of server to local np, but then syncing local to server is a pain
	//What if server file is being updated, how do we get a word in.
	//Realistically I can get away with a shitty implementation, but like wth.


	/*
	Note: This sync is shitty af. It works for our current purposes, but will need to be revised.
	Right now, we disregard currently updating files when syncing l->s and s->l.
	A possible solution is requiring a last hash when updating to be sure there were no very new changes
	*/
	public void trySync() throws ExecutionException, InterruptedException, IOException {
		List<LJournalEntity> localJournals = localRepo.database.getJournalDao()
				.loadAllAfterID(lastSyncLocalID).get();
		List<JsonObject> serverJournals = serverRepo.journalConn
				.getJournalEntriesAfter(lastSyncServerID);


		//There may be multiple journal entries with the same fileuid
		//Journal entries come in order of journalID (largest == latest)
		//We want an ordered map of journal entries by fileuid, with the largest journalID overwriting smaller ones

		Map<UUID, JsonObject> mapLocal = new LinkedHashMap<>();
		Map<UUID, JsonObject> mapServer = new LinkedHashMap<>();

		for(LJournalEntity journal : localJournals) {
			if(journal == null) continue;
			mapLocal.remove(journal.fileuid);
			mapLocal.put(journal.fileuid, new Gson().toJsonTree(journal).getAsJsonObject());
		}
		for(JsonObject journal : serverJournals) {
			if(journal == null) continue;
			UUID uuid = UUID.fromString(journal.get("fileuid").getAsString());
			mapServer.remove(uuid);
			mapServer.put(uuid, journal);
		}

		System.out.println("Local Entries: ");
		for(Map.Entry<UUID, JsonObject> entry : mapLocal.entrySet())
			System.out.println(entry.getValue());
		System.out.println("Server Entries: ");
		for(Map.Entry<UUID, JsonObject> entry : mapServer.entrySet())
			System.out.println(entry.getValue());



		//For each of the server journal entries
		for(Map.Entry<UUID, JsonObject> entry : mapServer.entrySet()) {
			//If we have conflicting local edits, we need to merge the files
			if(mapLocal.containsKey(entry.getKey()))
				merge(mapLocal.get(entry.getKey()), entry.getValue());
			else
				syncServerToLocal(entry.getValue());

			//Update the ID we've reached
			updateLastSyncServer(entry.getValue().get("journalid").getAsInt());
		}


		//Now that we've updated some files, reload the local map
		localJournals = localRepo.database.getJournalDao().loadAllAfterID(lastSyncLocalID).get();
		mapLocal.clear();
		for(LJournalEntity journal : localJournals) {
			if(journal == null) continue;
			mapLocal.remove(journal.fileuid);
			mapLocal.put(journal.fileuid, new Gson().toJsonTree(journal).getAsJsonObject());
		}


		//Now sync each local entry
		for(Map.Entry<UUID, JsonObject> entry : mapLocal.entrySet()) {
			syncLocalToServer(entry.getValue());
		}
	}


	//---------------------------------------------------------------------------------------------

	private void merge(JsonObject local, JsonObject server) throws IOException {
		System.out.println("Merging: "+local.get("fileuid"));

		//If the files are identical, just return
		if(local.equals(server))
			return;

		//Merging is going to take a significant amount of effort, so for now we're
		// just doing last writer wins.
		//TODO Don't know if these date conversions work from the different sql
		// Might need to convert to epoch during sql gets
		Date localDate = new Date(local.get("changetime").getAsLong());
		Date serverDate = new Date(server.get("changetime").getAsLong());
		if(localDate.after(serverDate))
			domainAPI.copyFileToServer(UUID.fromString(local.get("fileuid").getAsString()));
		else
			domainAPI.copyFileToLocal(UUID.fromString(server.get("fileuid").getAsString()));
	}


	private void syncLocalToServer(JsonObject localFile) throws IOException {
		System.out.println("Syncing to server: "+localFile.get("fileuid"));

		//For efficiency, check if the server's entry is actually any different before syncing
		JsonObject serverFile = serverRepo.fileConn
				.getProps(UUID.fromString(localFile.get("fileuid").getAsString()));

		//TODO If there is no file on server, don't sync
		//if(serverFile == null)

		//If the server does have different data, send over the local file
		if(!localFile.equals(serverFile))
			domainAPI.copyFileToServer(UUID.fromString(localFile.get("fileuid").getAsString()));
	}

	private void syncServerToLocal(JsonObject serverFile) throws IOException {
		System.out.println("Syncing to local: "+serverFile.get("fileuid"));

		//For efficiency, check if the local's entry is actually any different before syncing
		LFileEntity lFile = localRepo.database.getFileDao()
				.loadByUID(UUID.fromString(serverFile.get("fileuid").getAsString()));

		//If local no longer has the file, don't sync
		if(lFile == null) {
			System.out.println("Local does not have the file, skipping sync");
			return;
		}

		JsonObject localFile = new Gson().toJsonTree(lFile).getAsJsonObject();

		//If local does have different data, send over the server file
		if(!serverFile.equals(localFile)) {
			System.out.println("Local file has different data, syncing...");
			domainAPI.copyFileToLocal(UUID.fromString(serverFile.get("fileuid").getAsString()));
		}
		else
			System.out.println("Local file has identical data, skipping sync");
	}
}
