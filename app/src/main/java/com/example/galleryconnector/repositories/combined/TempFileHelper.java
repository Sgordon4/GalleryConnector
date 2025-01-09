package com.example.galleryconnector.repositories.combined;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;


//NOTE: We are assuming file contents are small

public class TempFileHelper {

	private static final String TAG = "Gal.GRepo.Temp";
	private final String tempDir = "temp";


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



	Map<UUID, StampedLock> fileLocks;

	//Use StampedLock



	public static TempFileHelper getInstance() {
		return TempFileHelper.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final TempFileHelper INSTANCE = new TempFileHelper();
	}
	private TempFileHelper() {
		fileLocks = new HashMap<>();
	}




	public long requestWriteLock(UUID fileUID) {
		if(!fileLocks.containsKey(fileUID))
			fileLocks.put(fileUID, new StampedLock());

		return fileLocks.get(fileUID).writeLock();
	}
	public void releaseWriteLock(UUID fileUID, long lockStamp) {
		if(!fileLocks.containsKey(fileUID))
			return;

		fileLocks.get(fileUID).unlockWrite(lockStamp);
	}
	public boolean isValidStamp(UUID fileUID, long stamp) {
		if(!fileLocks.containsKey(fileUID))
			return false;

		return fileLocks.get(fileUID).validate(stamp);
	}

	public void lockExample() {
		StampedLock stampedLock = new StampedLock();
		long stamp = stampedLock.writeLock();
		stampedLock.unlockWrite(stamp);
	}



	public void write(UUID fileUID, byte[] data, String lastHash, long lockStamp) throws IOException {
		if(!isValidStamp(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID: '"+fileUID+"'");

		File tempFile = getTempLocationOnDisk(fileUID);
		UserDefinedFileAttributeView attrs =
				Files.getFileAttributeView(tempFile.toPath(), UserDefinedFileAttributeView.class);



		//If the temp file already exists, we need to check that the lastHash matches
		if(tempFile.exists()) {
			ByteBuffer buffer = ByteBuffer.allocate(attrs.size("hash"));
			attrs.read("hash", buffer);
			String tempHash = buffer.toString();

			if(!Objects.equals(tempHash, lastHash))
				throw new IllegalStateException("Cannot write to temp file, hashes do not match!");
		}
		//If the temp file does not exist, we need to create it
		else {
			Files.createDirectories(tempFile.toPath().getParent());
			Files.createFile(tempFile.toPath());

			//And put the fileHash this change was based off of (hopefully from the Repos) in as an attribute
			attrs.write("hash", ByteBuffer.wrap(lastHash.getBytes()));
		}



		//Finally write to the temp file
		try(OutputStream out = Files.newOutputStream(tempFile.toPath());
			DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			//Write the data
			dos.write(data);

			//And the new fileHash
			String fileHash = ContentConnector.bytesToHex(dos.getMessageDigest().digest());
			attrs.write("hash", ByteBuffer.wrap(fileHash.getBytes()));
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}



	public boolean doesTempFileExist(UUID fileUID) {
		File tempFile = getTempLocationOnDisk(fileUID);
		return tempFile.exists();
	}

	@NonNull
	private File getTempLocationOnDisk(@NonNull UUID fileUID) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Temp files are stored in a temp subdirectory
		File tempRoot = new File(appDataDir, tempDir);

		//With each temp file named by the fileUID it represents
		return new File(tempRoot, fileUID.toString());
	}
}
