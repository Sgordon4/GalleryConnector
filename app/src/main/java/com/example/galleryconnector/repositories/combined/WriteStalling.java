package com.example.galleryconnector.repositories.combined;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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



	Map<UUID, StampedLock> fileLocks;

	//Use StampedLock



	public static WriteStalling getInstance() {
		return WriteStalling.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final WriteStalling INSTANCE = new WriteStalling();
	}
	private WriteStalling() {
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
