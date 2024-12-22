package com.example.galleryconnector.repositories.combined.sync;

import android.util.Log;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.combinedtypes.GJournal;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class SyncHandler {
	private static final String TAG = "Gal.SRepo.Sync";

	private int lastSyncLocalID;
	private int lastSyncServerID;

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
		lastSyncLocalID = 0;
		lastSyncServerID = 0;

		galleryRepo = GalleryRepo.getInstance();
		localRepo = LocalRepo.getInstance();
		serverRepo = ServerRepo.getInstance();

		domainAPI = DomainAPI.getInstance();
	}

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

		//Get props
		//Check hashes
		//Get contents
		//Merge
		//Write with hash included

		//Get the file from both repositories
		LFile localFile = localRepo.getFileProps(fileUID);
		if(localFile == null) return false;
		SFile serverFile = serverRepo.getFileProps(fileUID);
		if(serverFile == null) return false;


		//If the hashes of both files match, nothing needs to be synced
		if(Objects.equals(localFile.attrhash, serverFile.attrhash))
			return false;
		//If attrHash doesn't match, but fileHash does, the file contents are the same but other props are different
		else if(Objects.equals(localFile.filehash, serverFile.filehash)) {
			//We're just going with a last writer wins for now TODO Remake this

			//TODO Add IllegalState errors
			//Write whichever properties are most recent to their opposite repository
			if(localFile.changetime.isAfter(serverFile.changetime))
				galleryRepo.putFilePropsServer(GFile.fromLocalFile(localFile), null, serverFile.attrhash);	//Local is more recent
			else
				galleryRepo.putFilePropsLocal(GFile.fromServerFile(serverFile), null, localFile.attrhash);	//Server is more recent

			return true;
		}


		//TODO We need to compare journals to see if this was just an update. 


		//If the fileHashes differ, we unfortunately need to merge. TODO This can be improved with the use of previous Journal entries in each repo

		//Get the contents of each file so we can merge them
		//I'm pretty much just assuming any merges will be with small text files
		String localFileContents











		List<LJournal> localJournals = localRepo.database.getJournalDao().loadAllByFileUID(fileUID);
		List<SJournal> serverJournals = serverRepo.getJournalEntriesForFile(fileUID);

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
			LJournal local = localJournals.get(localIndex);
			SJournal server = serverJournals.get(serverIndex);

			merge(local, server);
		}


		//Data has been written, return true
		return true;
	}


	//Merging is going to take a significant amount of effort, so for now we're doing last writer wins.
	//Maybe we should just always copy from Server. Idk.
	public void merge(LJournal local, SJournal server) throws IOException {
		//TODO Don't know if these date conversions work from the different sql
		// Might need to convert to epoch during sql gets
		Instant localInstant = local.changetime;
		Instant serverInstant = server.changetime;

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

	public void trySyncAll() throws ExecutionException, InterruptedException, IOException {
		//Get all new journal entries
		List<LJournal> localJournals = localRepo.database.getJournalDao().loadAllAfterID(lastSyncLocalID);
		List<SJournal> serverJournals = serverRepo.getJournalEntriesAfter(lastSyncServerID);


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


		//For each fileUID, try to sync
		for(UUID fileUID : fileUIDs) {
			trySync(fileUID);
		}
	}
}
