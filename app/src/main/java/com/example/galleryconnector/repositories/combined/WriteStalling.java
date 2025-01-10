package com.example.galleryconnector.repositories.combined;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;


//NOTE: We are assuming file contents are small

public class WriteStalling {

	private static final String TAG = "Gal.GRepo.Temp";
	private final String storageDir = "writes";

	//TODO This isn't actually a "temp file" handler, it's more an intermediary write handler.
	// Pick a better name.

	//Create makeTempFileFor(UUID, byte[], lastHash)
	//Ideally write to closest repo every 5 seconds or so
	//When merging, write merged data to temp file first, then try to write to repo
	//Merge should occur whenever we get a listener ping, or whenever we attempt to persist temp file to repo
	//Job should delete temp file if temp file == sync file, and temp file is > ~3 seconds old
	//Decide how to merge based on isDir and isLink

	//Create a temp file right before importing/exporting, and right before reordering
	//What if we make the temp file the instant we start a drag for reordering?
	// We can add a little spinner after the drop if need be. I like this idea.


	/* Temp file writing and setup notes:
	 * - Writing should aim to be be extremely fast and painless
	 *
	 * - For an effective write, we need two things:
	 * 1. The data being written, which will be overwritten with each new write
	 * 2. A snapshot of the in-repo file contents BEFORE any writes, in case we need to merge later
	 *    This will just be a fileHash stored in the temp file's userAttributes referencing the actual content repos
	 *
	 * - We were going to make a sync-point file, but here's the deal:
	 *   > If we're persisting to local, that will occur within 5-10 seconds, in which time the
	 *     starting hash will not have been deleted and we can directly reference it.
	 *   > If we're persisting to server, the only way we don't persist within 5-10 seconds is if we
	 *     can't connect to the server at all, in which case we also probably can't get the sync point here.
	 *
	 * - Checking that a file exists will work if the file is on local, but not if the file is on server
	 *   and we can't connect. Because of this, I'm just choosing to let the client write to whatever
	 *   fileUID their heart desires, and if that file doesn't actually exist once we can write to both L&S,
	 *   then I think we toss the data to the void (in case the file was just deleted or something, idk).
	 */



	private final GalleryRepo grepo;
	private final Map<UUID, StampedLock> fileLocks;

	//Use StampedLock



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


	public boolean doesStallFileExist(UUID fileUID) {
		File stallFile = getStallFile(fileUID);
		return stallFile.exists();
	}


	public long requestWriteLock(UUID fileUID) {
		if(!fileLocks.containsKey(fileUID))
			fileLocks.put(fileUID, new StampedLock());

		return fileLocks.get(fileUID).writeLock();
	}
	public void releaseWriteLock(UUID fileUID, long stamp) {
		if(!fileLocks.containsKey(fileUID))
			return;

		fileLocks.get(fileUID).unlockWrite(stamp);
	}
	public boolean isStampValid(UUID fileUID, long stamp) {
		if(!fileLocks.containsKey(fileUID))
			return false;

		return fileLocks.get(fileUID).validate(stamp);
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	public String getStallFileAttribute(UUID fileUID, String attribute) throws FileNotFoundException {
		File stallFile = getStallFile(fileUID);
		if(!stallFile.exists())
			throw new FileNotFoundException("Stall file does not exist! FileUID='"+fileUID+"'");

		UserDefinedFileAttributeView attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);

		try {
			ByteBuffer buffer = ByteBuffer.allocate(attrs.size(attribute));
			attrs.read(attribute, buffer);
			return buffer.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getStallFileAttributes(UUID fileUID) throws IOException {
		File stallFile = getStallFile(fileUID);
		if(!stallFile.exists())
			throw new FileNotFoundException("Stall file does not exist! FileUID='"+fileUID+"'");

		UserDefinedFileAttributeView attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);
		Map<String, String> map = new HashMap<>();

		//For each attribute key:value pair, add to the map
		for(String attr : attrs.list()) {
			ByteBuffer buffer = ByteBuffer.allocate(attrs.size(attr));
			attrs.read(attr, buffer);
			map.put(attr, buffer.toString());
		}
		return map;
	}


	//---------------------------------------------------------------------------------------------


	public String write(UUID fileUID, byte[] data, String lastHash) throws IOException {
		File stallFile = getStallFile(fileUID);
		UserDefinedFileAttributeView attrs;

		//If the stall file already exists...
		if(stallFile.exists()) {
			attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);
			ByteBuffer buffer = ByteBuffer.allocate(attrs.size("hash"));
			attrs.read("hash", buffer);
			String writtenHash = buffer.toString();

			//Check that the last hash written to it matches what we've been given
			if(!Objects.equals(writtenHash, lastHash))
				throw new IllegalStateException("Invalid write to stall file, hashes do not match!");
		}
		//If the stall file does not exist...
		else {
			//We need to create it
			Files.createDirectories(stallFile.toPath().getParent());
			Files.createFile(stallFile.toPath());

			//And put the fileHash this change was based off of (hopefully from the Repos) in as an attribute
			attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);
			attrs.write("synchash", ByteBuffer.wrap(lastHash.getBytes()));
		}


		//Finally write to the stall file
		byte[] fileHash = writeData(stallFile, data);

		//Put the fileHash in as an attribute
		attrs.write("hash", ByteBuffer.wrap(fileHash));

		//And return the fileHash
		return ContentConnector.bytesToHex(fileHash);
	}


	//Note: Don't delete the lock for the file, as we don't actually care if it sits around until the app is closed
	// Other threads may be waiting for it however, so it's safest to leave it be
	public boolean delete(UUID fileUID) {
		File stallFile = getStallFile(fileUID);
		return stallFile.delete();
	}



	//---------------------------------------------------------------------------------------------


	//Persist a stall file to a repo (if the file already exists), merging if needed
	protected void persistStalledWrite(UUID fileUID, long lockStamp) {
		if(!isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");


		//----------------------------------------------------------------------
		//Make sure we actually need to persist


		//If there is no data to persist, do nothing
		if(!doesStallFileExist(fileUID))
			return;

		String stallHash;
		String syncHash;
		try {
			stallHash = getStallFileAttribute(fileUID, "hash");
			assert stallHash != null;
			syncHash = getStallFileAttribute(fileUID, "synchash");
		} catch (FileNotFoundException e) {	//Should never happen
			throw new RuntimeException(e);
		}


		//If the stall file has no updates since its last sync, everything should be up to date.
		boolean stallHasChanges = !Objects.equals(stallHash, syncHash);
		if(!stallHasChanges) {
			//It's likely that there haven't been any updates in the last 5 seconds, so now is a good time to delete the stall file
			delete(fileUID);
			return;
		}


		//----------------------------------------------------------------------
		//See if we can persist without merging


		//Get the properties of the existing file from the repos
		GFile existingFileProps;
		try {
			existingFileProps = grepo.getFileProps(fileUID);
		} catch (ConnectException e) {
			//If the file is not in local and we can't connect to the server, we can't write anything. Skip for now
			return;
		} catch (FileNotFoundException e) {
			//If the file is not in local OR server then there's nowhere to write, and either the client wrote to this UUID as a mistake
			// or the file was just deleted a few seconds ago. Either way, we can discard the data.
			delete(fileUID);
			return;
		}


		File stallFile = getStallFile(fileUID);

		//If the repository doesn't have any changes, we can write stall straight to repo
		boolean repoHasChanges = !Objects.equals(existingFileProps.filehash, syncHash);
		if(!repoHasChanges || existingFileProps.filehash == null) {
			//Find which repo to write to
			try {
				if(grepo.isFileLocal(fileUID)) {
					int fileSize = grepo.putContentsLocal(stallHash, Uri.fromFile(stallFile));

					existingFileProps.filehash = stallHash;
					existingFileProps.filesize = fileSize;
					existingFileProps.changetime = Instant.now().getEpochSecond();
					existingFileProps.modifytime = Instant.now().getEpochSecond();

					grepo.putFilePropsLocal(existingFileProps, syncHash, existingFileProps.attrhash);
				}
				else {
					int fileSize = grepo.putContentsServer(stallHash, stallFile);

					existingFileProps.filehash = stallHash;
					existingFileProps.filesize = fileSize;
					existingFileProps.changetime = Instant.now().getEpochSecond();
					existingFileProps.modifytime = Instant.now().getEpochSecond();

					grepo.putFilePropsServer(existingFileProps, syncHash, existingFileProps.attrhash);
				}
			} catch (FileNotFoundException | ContentsNotFoundException e) {
				throw new RuntimeException(e);
			}
			//If the file isn't local and we can't connect to the server, skip and try again later
			catch (ConnectException e) {
				return;
			}
		}


		//----------------------------------------------------------------------
		//We gotta merge...


		//Otherwise, since both the repo and the stall file have changes, we need to merge before we can persist
		else {
			try {
				Uri stallContents = Uri.fromFile(stallFile);
				Uri repoContents = grepo.getContentUri(existingFileProps.filehash);
				Uri syncContents = syncHash != null ? grepo.getContentUri(syncHash) : null;


				if(existingFileProps.isdir) {
					byte[] mergedContents = MergeUtilities.mergeDirectories(stallContents, repoContents, syncContents);
					write(fileUID, mergedContents, stallHash);
				}
				else if(existingFileProps.islink) {
					throw new RuntimeException("Stub!");
				}
				else {
					throw new RuntimeException("Stub!");
				}


			} //If the file isn't local and we can't connect to the server, skip and try again later
			catch (ConnectException e) {
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
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
	public File getStallFile(@NonNull UUID fileUID) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Temp files are stored in a temp subdirectory
		File tempRoot = new File(appDataDir, storageDir);

		//With each temp file named by the fileUID it represents
		return new File(tempRoot, fileUID.toString());
	}
}
