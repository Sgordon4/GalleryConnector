package com.example.galleryconnector.sync;

import com.example.galleryconnector.local.LocalRepo;
import com.example.galleryconnector.local.file.LFileEntity;
import com.example.galleryconnector.local.journal.LJournalEntity;
import com.example.galleryconnector.movement.DomainAPI;
import com.example.galleryconnector.server.ServerRepo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
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

	private final Map<UUID, JsonObject> mapLocal;
	private final Map<UUID, JsonObject> mapServer;


	public static SyncHandler getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final SyncHandler INSTANCE = new SyncHandler();
	}
	private SyncHandler() {
		lastSyncLocalID = 0;	//TODO Get from app props
		lastSyncServerID = 0;

		mapLocal = new HashMap<>();
		mapServer = new HashMap<>();

		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
	}




	//Bro HOW do we do this correctly? How do other people even do this correctly? Google?
	//We can sync all of server to local np, but then syncing local to server is a pain
	//What if server file is being updated, how do we get a word in.
	//Realistically I can get away with a shitty implementation, but like wth.


	public void trySync() throws ExecutionException, InterruptedException, IOException {
		List<LJournalEntity> localJournals = localRepo.database.getJournalDao()
				.loadAllAfterID(lastSyncLocalID).get();
		List<JsonObject> serverJournals = serverRepo.journalConn
				.getJournalEntriesAfter(lastSyncServerID);

		//TODO This is the new way we decided to do this:
		// - Loop through S and put in map, overwriting smaller IDs. Do the same for local.
		// - Loop through server map and make a list in order of ID
		// - Update all entries in local one by one, updating lastSyncServerID
		// Now what? How do we sync l->s while keeping l up to date? What if a server file is being
		// updated a lot? Do we need server to require a last hash when updating to be sure there
		// were no updates after sending the request? How do other companies even do this? I'm so confused.
		// Also we should prob split syncing local and syncing server to different functions with this.


		//Map the retrieved journal entries by fileuid. In case there are multiple entries
		// for the same fileuid, we want to keep the latest one (largest journalid).
		//They should be in order by ID when coming in, so we just have to loop through.
		for(LJournalEntity journal : localJournals)
			mapLocal.put(journal.fileuid, new Gson().toJsonTree(journal).getAsJsonObject());
		for(JsonObject journal : serverJournals)
			mapServer.put(UUID.fromString(journal.get("fileuid").getAsString()), journal);

		/*
		//Now, for each local journal entry we received...
		for(Map.Entry<UUID, JsonObject> entry : mapLocal.entrySet()) {
			//If there is a conflicting entry in our server map, we need to merge the files
			if(mapServer.containsKey(entry.getKey()))
				merge(entry.getValue(), mapServer.get(entry.getKey()));

			//Otherwise, we can just sync the local file to the server
			else syncLocalToServer(entry.getValue());

			//TODO What if we error? What do?
			//Now that we've synced, we're good to remove the entries
			mapLocal.remove(entry.getKey());
			mapServer.remove(entry.getKey());
		}

		//We've gone through all local entries, but there may still be server entries remaining
		for(Map.Entry<UUID, JsonObject> entry : mapServer.entrySet())
			syncServerToLocal(entry.getValue());


		//TODO Update IDs sometime before this (efficiently, if possible)
		 */

	}


	private void merge(JsonObject local, JsonObject server) throws IOException {
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
		//For efficiency, check if the local's entry is actually any different before syncing
		List<LFileEntity> lList = localRepo.database.getFileDao()
				.loadByUID(UUID.fromString(serverFile.get("fileuid").getAsString()));

		//If local no longer has the file, don't sync
		if(lList.isEmpty())
			return;

		JsonObject localFile = new Gson().toJsonTree(lList.get(0)).getAsJsonObject();

		//If local does have different data, send over the server file
		if(!serverFile.equals(localFile)) {
			domainAPI.copyFileToLocal(UUID.fromString(serverFile.get("fileuid").getAsString()));
		}
	}
}
