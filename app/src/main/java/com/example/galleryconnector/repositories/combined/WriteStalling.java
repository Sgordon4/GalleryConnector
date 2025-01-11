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
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.UserDefinedFileAttributeView;
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
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;


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


	@NonNull
	public List<UUID> listStallFiles() {
		File stallDir = getStallFile(UUID.randomUUID()).getParentFile();
		if(!stallDir.exists())
			return new ArrayList<>();

		return Arrays.stream(stallDir.list()).map(UUID::fromString).collect(Collectors.toList());
	}

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


	//Speedy fast
	public String write(UUID fileUID, byte[] data, String lastHash) {
		File stallFile = getStallFile(fileUID);

		//If the stall file already exists...
		if(stallFile.exists()) {
			//Check that the last hash written to it matches what we've been given
			String writtenHash = getAttribute(fileUID, "hash");
			if(!Objects.equals(writtenHash, lastHash))
				throw new IllegalStateException("Invalid write to stall file, hashes do not match!");
		}

		//If the stall file does not exist...
		else {
			try {
				System.out.println("Creating stall file");
				//We need to create it
				Files.createDirectories(stallFile.toPath().getParent());
				Files.createFile(stallFile.toPath());

				System.out.println("Created "+stallFile.toPath());

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
			putAttribute(fileUID, "hash", fileHash);

			//And return the fileHash
			return ContentConnector.bytesToHex(fileHash);
		}
		catch (IOException e) { throw new RuntimeException(e); }
	}


	//Note: Don't delete the lock for the file as other threads may be waiting for it
	public boolean delete(UUID fileUID) {
		File stallFile = getStallFile(fileUID);
		return stallFile.delete();
	}



	//---------------------------------------------------------------------------------------------


	//Persist a stall file to a repo (if the file already exists), merging if needed
	//This method is long as fuck, but realistically should be super fast to run. Unless we need to merge.
	protected void persistStalledWrite(UUID fileUID, long lockStamp) {
		//If there is no data to persist, do nothing
		if(!doesStallFileExist(fileUID))
			return;

		if(!isStampValid(fileUID, lockStamp))
			throw new IllegalStateException("Invalid lock stamp! FileUID='"+fileUID+"'");


		File stallFile = getStallFile(fileUID);
		String stallHash = getAttribute(fileUID, "hash");
		assert stallHash != null;
		String syncHash = getAttribute(fileUID, "synchash");

		//If the stall file has had no updates since its last sync, everything should be up to date
		boolean stallHasChanges = !Objects.equals(stallHash, syncHash);
		if(!stallHasChanges) {
			//It's likely that there haven't been any updates in the last 5 seconds, so now is a good time to delete the stall file
			delete(fileUID);
			return;
		}



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


		//----------------------------------------------------------------------
		//See if we can persist without merging


		//If the repository doesn't have any changes, we can write stall straight to repo
		boolean repoHasChanges = !Objects.equals(existingFileProps.filehash, syncHash);
		if(!repoHasChanges) {
			//Find which repo to write to
			try {
				existingFileProps.filehash = stallHash;
				existingFileProps.filesize = (int) stallFile.length();
				existingFileProps.changetime = Instant.now().getEpochSecond();
				existingFileProps.modifytime = Instant.now().getEpochSecond();

				if(grepo.isFileLocal(fileUID)) {
					grepo.putContentsLocal(stallHash, Uri.fromFile(stallFile));
					grepo.putFilePropsLocal(existingFileProps, syncHash, existingFileProps.attrhash);
				}
				else {
					try {
						grepo.putContentsServer(stallHash, stallFile);
						grepo.putFilePropsServer(existingFileProps, syncHash, existingFileProps.attrhash);
					}
					//If the file isn't local and we can't connect to the server, skip and try again later
					catch (ConnectException e) {
						return;
					}
				}
			}
			catch (ContentsNotFoundException | FileNotFoundException e) {
				throw new RuntimeException(e);
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
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}


	//---------------------------------------------------------------------------------------------


	@Nullable
	private String getAttribute(UUID fileUID, String attribute) /*throws FileNotFoundException*/ {
		File stallFile = getStallFile(fileUID);

		//We've only been using this method after checking that the file exists, and this is getting in the way
		//if(!stallFile.exists())
		//	throw new FileNotFoundException("Stall file does not exist! FileUID='"+fileUID+"'");

		try {
			UserDefinedFileAttributeView attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);

			ByteBuffer readBuffer = ByteBuffer.allocate( attrs.size(attribute) );
			attrs.read(attribute, readBuffer);
			return new String(readBuffer.array());

		} catch (NoSuchFileException e) {
			//Attribute does not exist
			return null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void putAttribute(UUID fileUID, String key, String value) /*throws FileNotFoundException*/ {
		putAttribute(fileUID, key, value.getBytes());
	}
	private void putAttribute(UUID fileUID, String key, byte[] value) /*throws FileNotFoundException*/ {
		File stallFile = getStallFile(fileUID);

		System.out.println("Putting attributes in "+stallFile.toPath());
		System.out.println("Exists: "+stallFile.exists());

		//We've only been using this method after checking that the file exists, and this is getting in the way
		//if(!stallFile.exists())
		//	throw new FileNotFoundException("Stall file does not exist! FileUID='"+fileUID+"'");

		try {
			boolean supports = Files.getFileStore(stallFile.toPath()).supportsFileAttributeView(UserDefinedFileAttributeView.class);
			System.out.println("DoesSupport:"+supports);
			UserDefinedFileAttributeView attrs = Files.getFileAttributeView(stallFile.toPath(), UserDefinedFileAttributeView.class);



			ByteBuffer buffer = ByteBuffer.wrap(value);
			System.out.println("isNull?");
			System.out.println("attrs:"+attrs);
			System.out.println("buffer:"+buffer);
			System.out.println("key:"+key);


			attrs.write(key, buffer);
		} catch (IOException e) {
			throw new RuntimeException(e);
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

		//Stall files are stored in a stall subdirectory
		File tempRoot = new File(appDataDir, storageDir);

		//With each file named by the fileUID it represents
		return new File(tempRoot, fileUID.toString());
	}
}
