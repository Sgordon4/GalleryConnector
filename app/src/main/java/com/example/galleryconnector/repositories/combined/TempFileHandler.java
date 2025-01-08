package com.example.galleryconnector.repositories.combined;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.UUID;


//NOTE: We are assuming file contents are small

public class TempFileHandler {

	private static final String TAG = "Gal.GRepo.Temp";
	private final String tempDir = "temp";

	private final GalleryRepo grepo = GalleryRepo.getInstance();


	//Create makeTempFileFor(UUID, byte[], lastHash)
	//Ideally write to closest repo every 5 seconds or so
	//When merging, write merged data to temp file first, then try to write to repo
	//Merge should occur whenever we get a listener ping, or whenever we attempt to persist temp file to repo
	//Job should delete temp file if temp file == sync file, and temp file is > ~3 seconds old
	//Decide how to merge based on isDir and isLink

	//Create a temp file right before importing/exporting, and right before reordering
	//What if we make the temp file the instant we start a drag for reordering?
	// We can add a little spinner after the drop if need be. I like this idea.


	//We should use this because the other option requires a side thread
	public void createTempFile(UUID fileUID, byte[] syncedData) {

	}


	//Creates a temp file with the contents of the given UUID, as well as a last-sync file to accompany it (with the same data)
	public void createTempFileFor(UUID fileUID) throws
			FileAlreadyExistsException, FileNotFoundException, ConnectException, ContentsNotFoundException {
		File tempFile = getTempLocationOnDisk(fileUID);
		File syncFile = getSyncPointLocationOnDisk(fileUID);

		if(tempFile.exists()) throw new FileAlreadyExistsException("Temp file already exists for fileUID='"+fileUID+"'");

		GFile fileProps = grepo.getFileProps(fileUID);
		byte[] contents = new byte[fileProps.filesize];

		//If there is data to read, read it
		if(fileProps.filehash != null) {
			try {
				URL contentUri = new URL(grepo.getContentUri(fileProps.filehash).toString());
				try (BufferedInputStream in = new BufferedInputStream( contentUri.openStream() )) {
					in.read(contents);
				}
				catch (IOException e) { throw new RuntimeException(e); }
			} catch (MalformedURLException e) { throw new RuntimeException(e); }
		}


		//Now create the temp file pair and write the retrieved content to them
		try {
			Files.createDirectories(tempFile.toPath().getParent());


			Files.createFile(tempFile.toPath());
			try (FileOutputStream out = new FileOutputStream(tempFile)) {
				out.write(contents);
			}
			UserDefinedFileAttributeView tempAttrs = Files.getFileAttributeView(tempFile.toPath(), UserDefinedFileAttributeView.class);
			tempAttrs.write("hash", ByteBuffer.wrap(fileProps.filehash.getBytes()));


			Files.createFile(syncFile.toPath());
			try (FileOutputStream out = new FileOutputStream(syncFile)) {
				out.write(contents);
			}
			UserDefinedFileAttributeView syncAttrs = Files.getFileAttributeView(syncFile.toPath(), UserDefinedFileAttributeView.class);
			syncAttrs.write("hash", ByteBuffer.wrap(fileProps.filehash.getBytes()));


		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//WARNING: Blocks if a temp file needs to be createdk
	public void writeToTempFile(UUID fileUID, byte[] data, String lastTempHash) throws IOException {
		File tempFile = getTempLocationOnDisk(fileUID);
		File syncFile = getSyncPointLocationOnDisk(fileUID);

		if(!tempFile.exists()) throw new FileNotFoundException("Temp file does not exist for fileUID='"+fileUID+"'");


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
