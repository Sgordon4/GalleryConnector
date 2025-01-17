package com.example.galleryconnector.repositories.combined.jobs.writestalling;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.MergeUtilities;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import javax.xml.transform.Result;


//NOTE: We are assuming file contents are small

public class WriteStalling {

	private static final String TAG = "Gal.GRepo.Stall";
	private final String storageDir = "writes";

	private static final boolean debug = true;



	/* Stall file writing and setup notes:
	 * - Writing should aim to be be extremely fast and painless.
	 *
	 * - Contents being written should be small enough to hold multiple in memory.
	 * 	  (Ideally short text files or directory listings)
	 *
	 * - Stall files should not be used to import files, only updating existing ones.
	 *
	 * - For an effective write, we need two things:
	 * 1. The data being written, which will be overwritten with each new write.
	 * 2. A snapshot of the in-repo file contents BEFORE any writes, in case we need to merge later.
	 *    This will just be a fileHash referencing the actual content repos.
	 *
	 * - We were going to make a sync-point file, but here's the deal:
	 *   > If we're persisting to local, that will occur within 5-10 seconds, in which time the
	 *     content for the sync-point hash will not have been deleted and we can directly reference it.
	 *   > If we're persisting to server, the only way we don't persist within 5-10 seconds is if we
	 *     can't connect to the server at all, in which case we also probably can't get the sync point anyway.
	 *
	 * - Checking that a file exists will work if the file is on local, but not if the file is on server
	 *   and we can't connect. Because of this, I'm just choosing to let the client write to whatever
	 *   fileUID their heart desires, and if that file doesn't actually exist once we can write to both L&S,
	 *   then I think we toss the data to the void (in case the file was just deleted or something, idk).
	 */



	private final GalleryRepo grepo;
	private final Map<UUID, StampedLock> fileLocks;


	public static WriteStalling getInstance() {
		return WriteStalling.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final WriteStalling INSTANCE = new WriteStalling();
	}
	private WriteStalling() {
		grepo = GalleryRepo.getInstance();
		fileLocks = new HashMap<>();
	}


	//---------------------------------------------------------------------------------------------


	@NonNull
	public List<UUID> listStallFiles() {
		File stallDir = getStallFile(UUID.randomUUID()).getParentFile();
		if(!stallDir.exists())
			return new ArrayList<>();

		return Arrays.stream(stallDir.list())
				.filter(f -> !f.endsWith(".metadata"))
				.map(UUID::fromString)
				.filter(uuid -> !Objects.equals(getAttribute(uuid, "isHidden"), "true"))
				.collect(Collectors.toList());
	}

	public boolean doesStallFileExist(@NonNull UUID fileUID) {
		File stallFile = getStallFile(fileUID);
		return stallFile.exists() && !Objects.equals(getAttribute(fileUID, "isHidden"), "true");
	}



	public long requestWriteLock(@NonNull UUID fileUID) {
		if(!fileLocks.containsKey(fileUID))
			fileLocks.put(fileUID, new StampedLock());

		return fileLocks.get(fileUID).writeLock();
	}
	public void releaseWriteLock(@NonNull UUID fileUID, long stamp) {
		if(!fileLocks.containsKey(fileUID))
			return;

		fileLocks.get(fileUID).unlockWrite(stamp);
	}
	public boolean isStampValid(@NonNull UUID fileUID, long stamp) {
		if(!fileLocks.containsKey(fileUID))
			return false;

		return fileLocks.get(fileUID).validate(stamp);
	}



	private void createStallFile(@NonNull UUID fileUID) throws IOException {
		File stallFile = getStallFile(fileUID);
		File metadataFile = getMetadataFile(fileUID);

		if(!stallFile.exists()) {
			//Create the stall file
			Files.createDirectories(stallFile.toPath().getParent());
			Files.createFile(stallFile.toPath());
		}
		if(!metadataFile.exists()) {
			//And create its companion metadata file
			Files.createDirectories(metadataFile.toPath().getParent());
			Files.createFile(metadataFile.toPath());
		}


		//If the file was actually just hidden, cancel the delete job
		if(Objects.equals(getAttribute(fileUID, "isHidden"), "true")) {
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			workManager.cancelUniqueWork("stall_"+fileUID);

			//Make sure the metadata file is empty as it would be if we just created it
			Files.write(metadataFile.toPath(), new byte[0]);

			//Don't delete the stallFile data in case something is STILL reading it somehow,
			// just pretend there is none and overwrite it later
		}
	}


	//Directly deleting the file may be problematic if another thread has a content uri and is trying to display it.
	//Therefore we need to mark it as hidden so we don't use it anymore,
	// and launch a job to actually delete it a few minutes into the future.
	//Note: Don't delete the lock for the file as other threads may be waiting for it
	public void delete(@NonNull UUID fileUID) {
		File stallFile = getStallFile(fileUID);
		File stallMetadata = getMetadataFile(fileUID);

		//If the file already doesn't exist, we're done here
		if(!stallFile.exists()) {
			if(stallMetadata.exists())
				stallMetadata.delete();
			return;
		}


		//For efficiency, make sure we haven't already 'deleted' the files
		String isHidden = getAttribute(fileUID, "isHidden");
		if(Objects.equals(isHidden, "true"))
			return;


		//Assuming stallMetadata exists if stallFile exists...
		//Mark the file as hidden
		putAttribute(fileUID, "isHidden", String.valueOf(true));

		//And launch a Delete worker to delete the files
		WriteStallWorkers.launchDeleteWorker(fileUID);
	}


	//---------------------------------------------------------------------------------------------


	//Speedy fast
	public String write(@NonNull UUID fileUID, @NonNull byte[] data, @Nullable String lastHash) {
		File stallFile = getStallFile(fileUID);

		//If the stall file already exists...
		if(doesStallFileExist(fileUID)) {
			//Check that the last hash written to it matches what we've been given
			String writtenHash = getAttribute(fileUID, "hash");
			if(!Objects.equals(writtenHash, lastHash))
				throw new IllegalStateException("Invalid write to stall file, hashes do not match!");
		}

		//If the stall file does not exist...
		else {
			try {
				//We need to create it
				createStallFile(fileUID);

				//We can't guarantee we can reach the existing fileProps (server connection), so for speed we'll need to use the passed lastHash as the sync-point
				//Not connecting to check also has the side effect of allowing a write to a fileUID that doesn't exist, but we can just discard later if so
				putAttribute(fileUID, "synchash", lastHash);
			}
			catch (IOException e) { throw new RuntimeException(e); }
		}


		try {
			//Finally write the new data to the stall file
			byte[] fileHash = writeData(stallFile, data);

			//Put the fileHash in as an attribute
			putAttribute(fileUID, "hash", ContentConnector.bytesToHex(fileHash));

			//And return the fileHash
			return ContentConnector.bytesToHex(fileHash);
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}


	//---------------------------------------------------------------------------------------------


	//Persist a stall file to a repo (if the file already exists), merging if needed
	//This method is long as fuck, but realistically should be super fast to run. Unless we need to merge.
	public void persistStalledWrite(@NonNull UUID fileUID) throws IllegalStateException, ConnectException {
		Log.i(TAG, String.format("PERSIST STALLFILE called with fileUID='%s'", fileUID));
		//If there is no data to persist, do nothing
		if(!doesStallFileExist(fileUID)) {
			if(debug) Log.d(TAG, "No stall file to persist, skipping.");
			return;
		}


		File stallFile = getStallFile(fileUID);
		String stallHash = getAttribute(fileUID, "hash");
		assert stallHash != null;
		String syncHash = getAttribute(fileUID, "synchash");
		if(debug) Log.d(TAG, String.format("Hashes are '%s'::'%s'", stallHash, syncHash));

		//If the stall file has had no updates since its last sync, everything should be up to date
		boolean stallHasChanges = !Objects.equals(stallHash, syncHash);
		if(!stallHasChanges) {
			if(debug) Log.d(TAG, String.format("Stall file hash identical to sync-point, deleting stall file. fileUID='%s'", fileUID));
			//It's likely that there haven't been any updates in the last 5 seconds, so now is a good time to delete the stall file
			delete(fileUID);
			return;
		}



		//Get the properties of the existing file from the repos
		GFile existingFileProps;
		try {
			existingFileProps = grepo.getFileProps(fileUID);
		} catch (ConnectException e) {
			if(debug) Log.d(TAG, "File props not found locally, cannot connect to server, skipping.");
			//If the file is not in local and we can't connect to the server, we can't write anything. Skip for now
			throw e;
		} catch (FileNotFoundException e) {
			if(debug) Log.d(TAG, String.format("File props not found in either repo, deleting stall file. fileUID='%s'", fileUID));
			//If the file is not in local OR server then there's nowhere to write, and either the client wrote to this UUID as a mistake
			// or the file was just deleted a few seconds ago. Either way, we can discard the data.
			delete(fileUID);
			return;
		}


		//----------------------------------------------------------------------
		//See if we can persist without merging




		boolean repoHasChanges = !Objects.equals(existingFileProps.filehash, syncHash);


		//TODO Merging is very hard, and my brain is very smooth.
		// Therefore, I am setting it so the stall file is ALWAYS written to the repo instead of merging.
		// This should work for a one-device-per-account setup like we'll have initially, but
		// MUST be rectified for this to be respectable
		if(repoHasChanges)
			Log.e(TAG, "StallFile write was supposed to merge! fileUID='"+fileUID+"'");
		repoHasChanges = false;


		//If the repository doesn't have any changes, we can write stall straight to repo
		if(!repoHasChanges || existingFileProps.filehash == null) {
			if(debug) Log.d(TAG, "Repo identical to sync-point, persisting stall file with no changes.");

			try {
				//Update the existing file props with the new data from the stallFile
				existingFileProps.filehash = stallHash;
				existingFileProps.filesize = (int) stallFile.length();
				existingFileProps.changetime = Instant.now().getEpochSecond();
				existingFileProps.modifytime = Instant.now().getEpochSecond();

				//Find which repo to write to. Since we JUST got the props from one of them, if it's not in local then it's definitely on the server.
				if(grepo.isFileLocal(fileUID)) {
					if(debug) Log.d(TAG, "Persisting locally.");
					if(!grepo.doesContentExistLocal(stallHash))
						grepo.putContentsLocal(stallHash, Uri.fromFile(stallFile));

					grepo.putFilePropsLocal(existingFileProps, syncHash, existingFileProps.attrhash);
				}
				else {
					try {
						if(debug) Log.d(TAG, "Persisting on Server.");
						if(!grepo.doesContentExistServer(stallHash))
							grepo.putContentsServer(stallHash, stallFile);
						grepo.putFilePropsServer(existingFileProps, syncHash, existingFileProps.attrhash);
					}
					//If the file isn't local and we can't connect to the server, skip and try again later
					catch (ConnectException e) {
						if(debug) Log.d(TAG, "File not local, no connection to server. Could not persist, skipping.");
						throw e;
					}
				}
			} catch (IllegalStateException e) {
				//File hashes didn't match, meaning there was an update while we were doing this. Skip and try again later
				Log.d(TAG, "Hashes were updated in the background while persisting stallFile! fileUID='"+fileUID+"'");
				throw e;
			} catch (ContentsNotFoundException | FileNotFoundException e) {
				//Uh oh!
				throw new RuntimeException(e);
			}
		}


		//----------------------------------------------------------------------
		//We gotta merge...


		//Otherwise, since both the repo and the stall file have changes, we need to merge before we can persist
		else {
			if(debug) Log.d(TAG, "Repo has changes, merging with stall file.");

			try {
				Uri stallContents = Uri.fromFile(stallFile);
				Uri repoContents = grepo.getContentUri(existingFileProps.filehash);
				Uri syncContents = syncHash == null ? null : grepo.getContentUri(syncHash);


				if(existingFileProps.isdir) {
					byte[] mergedContents = MergeUtilities.mergeDirectories(stallContents, repoContents, syncContents);
					write(fileUID, mergedContents, stallHash);
				}
				else if(existingFileProps.islink) {
					byte[] mergedContents = MergeUtilities.mergeLinks(stallContents, repoContents, syncContents);
					write(fileUID, mergedContents, stallHash);
				}
				else {
					byte[] mergedContents = MergeUtilities.mergeNormal(stallContents, repoContents, syncContents);
					write(fileUID, mergedContents, stallHash);
				}
			}
			//If the file isn't local and we can't connect to the server, skip and try again later
			catch (ConnectException e) {
				if(debug) Log.d(TAG, "File not local, no connection to server. Could not merge, skipping.");
				throw e;
			} catch (IllegalStateException e) {
				//File hashes didn't match, meaning there was an update while we were doing this. Skip and try again later
				Log.d(TAG, "Hashes were updated in the background while persisting stallFile! fileUID='"+fileUID+"'");
				throw e;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		Log.d(TAG, "Persisting stallFile was successful!");
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	protected String getAttribute(@NonNull UUID fileUID, @NonNull String attribute) {
		try {
			JsonObject props = readAttributes(fileUID);
			JsonElement prop = props.get(attribute);;

			return prop == null ? null : prop.getAsString();
		}
		catch (FileNotFoundException e) { return null; }
	}

	private void putAttribute(@NonNull UUID fileUID, @NonNull String key, @Nullable String value) {
		try {
			JsonObject props = readAttributes(fileUID);
			props.addProperty(key, value);

			writeAttributes(fileUID, props);
		}
		catch (FileNotFoundException e) { return; }
	}




	@NonNull
	private JsonObject readAttributes(@NonNull UUID fileUID) throws FileNotFoundException {
		File metadataFile = getMetadataFile(fileUID);
		if(!metadataFile.exists())
			throw new FileNotFoundException("Stall metadata file does not exist! FileUID='"+fileUID+"'");

		if(metadataFile.length() == 0)
			return new JsonObject();

		try (BufferedReader br = new BufferedReader(new FileReader(metadataFile))) {
			return new Gson().fromJson(br, JsonObject.class);
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}


	private void writeAttributes(@NonNull UUID fileUID, @NonNull JsonObject props) throws FileNotFoundException {
		File metadataFile = getMetadataFile(fileUID);
		if(!metadataFile.exists())
			throw new FileNotFoundException("Stall metadata file does not exist! FileUID='"+fileUID+"'");

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(metadataFile))) {
			new Gson().toJson(props, bw);
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}



	//---------------------------------------------------------------------------------------------


	//Helper method, returns fileHash
	private byte[] writeData(File file, byte[] data) throws IOException {
		try(OutputStream out = Files.newOutputStream(file.toPath());
			DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			dos.write(data);

			//Return the fileHash calculated when we wrote the file
			return dos.getMessageDigest().digest();
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}


	@NonNull
	/*protected*/ public File getStallFile(@NonNull UUID fileUID) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Stall files are stored in a stall subdirectory
		File tempRoot = new File(appDataDir, storageDir);

		//With each file named by the fileUID it represents
		return new File(tempRoot, fileUID.toString());
	}
	@NonNull
	protected File getMetadataFile(@NonNull UUID fileUID) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Stall files are stored in a stall subdirectory
		File tempRoot = new File(appDataDir, storageDir);

		//With each file named by the fileUID it represents
		return new File(tempRoot, fileUID.toString()+".metadata");
	}
}
