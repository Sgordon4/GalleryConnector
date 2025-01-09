package com.example.galleryconnector.repositories.combined;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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




	//When writing, make sure the lock was actually requested


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


	public void write(UUID fileUID, byte[] data, String lastTempHash, long lockStamp) {
		throw new RuntimeException("Stub!");
	}




	public void createTempFile(String name, byte[] data) {
		throw new RuntimeException("Stub!");
	}

	public boolean doesTempFileExist(String fileName) {
		File tempFile = getTempLocationOnDisk(fileName);
		return tempFile.exists();
	}



	@NonNull
	private File getTempLocationOnDisk(@NonNull String fileName) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Temp files are stored in a temp subdirectory
		File tempRoot = new File(appDataDir, tempDir);

		//With each temp file named by the fileUID it represents
		return new File(tempRoot, fileName);
	}







	public void testLock() {
		StampedLock stampedLock = new StampedLock();
		long stamp = stampedLock.writeLock();
		stampedLock.unlockWrite(stamp);
	}







	//This should be called with the content from the repo, not the new data we hope to write to the temp file
	public void createTempFile(UUID fileUID, byte[] syncedData) throws FileAlreadyExistsException {
		File tempFile = getTempLocationOnDisk(fileUID);
		File syncFile = getSyncPointLocationOnDisk(fileUID);

		if(tempFile.exists()) throw new FileAlreadyExistsException("Temp file already exists for fileUID='"+fileUID+"'");


		//Create the temp file pair and write the data to them
		try {
			Files.createDirectories(tempFile.toPath().getParent());
			Files.createFile(tempFile.toPath());


			try (DigestOutputStream out = new DigestOutputStream(Files.newOutputStream(tempFile.toPath()), MessageDigest.getInstance("SHA-256"))) {
				out.write(syncedData);
				String fileHash = ContentConnector.bytesToHex(out.getMessageDigest().digest());

				UserDefinedFileAttributeView attrs = Files.getFileAttributeView(tempFile.toPath(), UserDefinedFileAttributeView.class);
				attrs.write("hash", ByteBuffer.wrap(fileHash.getBytes()));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}


			try (DigestOutputStream out = new DigestOutputStream(Files.newOutputStream(syncFile.toPath()), MessageDigest.getInstance("SHA-256"))) {
				out.write(syncedData);
				String fileHash = ContentConnector.bytesToHex(out.getMessageDigest().digest());

				UserDefinedFileAttributeView attrs = Files.getFileAttributeView(syncFile.toPath(), UserDefinedFileAttributeView.class);
				attrs.write("hash", ByteBuffer.wrap(fileHash.getBytes()));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	public void writeToTempFile(UUID fileUID, byte[] data, String lastTempHash) throws IOException {
		File tempFile = getTempLocationOnDisk(fileUID);
		File syncFile = getSyncPointLocationOnDisk(fileUID);

		if(!tempFile.exists()) throw new FileNotFoundException("Temp file does not exist for fileUID='"+fileUID+"'");

		//Get the current hash of the temp file
		UserDefinedFileAttributeView attrs = Files.getFileAttributeView(syncFile.toPath(), UserDefinedFileAttributeView.class);
		ByteBuffer buffer = ByteBuffer.allocate(attrs.size("hash"));
		attrs.read("hash", buffer);
		String tempHash = buffer.toString();

		//If the hashes don't match, yell loudly
		if(!Objects.equals(tempHash, lastTempHash))
			throw new IllegalStateException("Hashes do not match. Current hash='"+tempHash+"'");


		//TODO Working here

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
	private File getSyncPointLocationOnDisk(@NonNull UUID fileUID) {
		return new File(getTempLocationOnDisk(fileUID).getParent(), fileUID+".sync");
	}
}
