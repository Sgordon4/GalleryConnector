package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.writestalling.WriteStalling;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TestWriteStalling {
	GalleryRepo grepo = GalleryRepo.getInstance();
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	WriteStalling writeStalling = WriteStalling.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	String sampleData = "Sample";


	private void clearStallFiles() {
		List<UUID> stallFiles = writeStalling.listStallFiles();
		for(UUID uuid : stallFiles) {
			writeStalling.delete(uuid);
		}
		stallFiles = writeStalling.listStallFiles();
		assert stallFiles.isEmpty();
	}


	public String testWrite(String syncHash) throws IOException {
		System.out.println("Testing write...");
		//Write to the stall file
		clearStallFiles();

		String fileHash = writeStalling.write(fileUID, sampleData.getBytes(), syncHash);
		System.out.println("FileHash: " + fileHash);

		List<UUID> stallFiles = writeStalling.listStallFiles();
		assert stallFiles.size() == 1;

		//Make sure the contents are as they should be
		byte[] contents = Files.readAllBytes(writeStalling.getStallFile(fileUID).toPath());
		assert contents.length == sampleData.length();
		assert new String(contents).equals(sampleData);

		System.out.println("Write test complete!");
		return fileHash;
	}

	public void testDeleteWorker() throws IOException {
		File stallFile = writeStalling.getStallFile(fileUID);

		clearStallFiles();
		assert !stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);



		System.out.println("Deleting a file that doesn't exist...");
		//Try to delete a file that doesn't exist
		writeStalling.delete(fileUID);
		//Make sure the file still does not exist
		assert !stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);

		//Wait to see if the operation runs
		try { Thread.sleep(5000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure the file still does not exist
		assert !stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);



		System.out.println("Writing to file for the first time...");
		//Write to the stall file to create it
		writeStalling.write(fileUID, sampleData.getBytes(), "null");

		//Make sure the contents are as they should be
		assert stallFile.exists();
		assert writeStalling.doesStallFileExist(fileUID);
		byte[] contents = Files.readAllBytes(stallFile.toPath());
		assert contents.length == sampleData.length();
		assert new String(contents).equals(sampleData);



		System.out.println("Marking file for deletion...");
		//Mark it for deletion
		writeStalling.delete(fileUID);
		//Make sure it was marked for deletion but wasn't immediately deleted
		assert stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);



		System.out.println("Undeleting file...");
		//Then immediately write again to undelete it
		String newSampleData = "Otherdata";
		writeStalling.write(fileUID, newSampleData.getBytes(), "null");

		//Make sure the contents are as they should be
		assert stallFile.exists();
		assert writeStalling.doesStallFileExist(fileUID);
		contents = Files.readAllBytes(writeStalling.getStallFile(fileUID).toPath());
		assert contents.length == newSampleData.length();
		assert new String(contents).equals(newSampleData);



		//And wait to make sure the previous deletion does NOT take place
		try { Thread.sleep(5000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }
		System.out.println("Did delete worker run incorrectly?");



		//Make sure the contents are as they should be
		assert stallFile.exists();
		assert writeStalling.doesStallFileExist(fileUID);
		contents = Files.readAllBytes(writeStalling.getStallFile(fileUID).toPath());
		assert contents.length == newSampleData.length();
		assert new String(contents).equals(newSampleData);



		System.out.println("ACTUALLY marking the file for deletion...");
		//Now ACTUALLY mark it for deletion
		writeStalling.delete(fileUID);
		//Make sure it was marked for deletion but wasn't immediately deleted
		assert stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);

		//Wait to see if the delete takes place
		try { Thread.sleep(5000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }



		System.out.println("Asserting that things have been removed...");
		//Make sure the file was actually deleted
		assert !stallFile.exists();
		assert !writeStalling.doesStallFileExist(fileUID);


		System.out.println("Delete test completed!");
	}



	public void testPersistLocal() throws IOException {
		grepo.deleteFilePropsLocal(fileUID);
		grepo.deleteFilePropsServer(fileUID);
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		GFile newFile = new GFile(fileUID, accountUID);
		newFile = grepo.createFilePropsLocal(newFile);
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);



		String writtenFileHash = testWrite(newFile.filehash);

		//And persist the write to the closest repo that has the file
		writeStalling.persistStalledWrite(fileUID);



		//Make sure the data is in the correct repo
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//Read the contents from the repo
		GFile fileProps = grepo.getFileProps(fileUID);

		//Make sure the data has been updated
		assert fileProps.filehash != null;
		assert fileProps.filehash.equals(writtenFileHash);

		//Note: We don't delete the actual content from either repo before doing this,
		// so it isn't actually written by the stallFile write if it already exists.
		//Because of this I'm not checking, and the fileHash is enough to be sure


		System.out.println("Persist test complete!");
	}


	public void testPersistServer() throws IOException {
		grepo.deleteFilePropsLocal(fileUID);
		grepo.deleteFilePropsServer(fileUID);
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		GFile newFile = new GFile(fileUID, accountUID);
		grepo.createFilePropsServer(newFile);
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Write to the stall file
		String writtenFileHash = testWrite(newFile.filehash);

		//And persist the write to the closest repo that has the file
		writeStalling.persistStalledWrite(fileUID);



		//Make sure the data is in the correct repo
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Read the contents from the repo
		GFile fileProps = grepo.getFileProps(fileUID);

		//Make sure the data has been updated
		assert fileProps.filehash != null;
		assert fileProps.filehash.equals(writtenFileHash);

		//Note: We don't delete the actual content from either repo before doing this,
		// so it isn't actually written by the stallFile write if it already exists.
		//Because of this I'm not checking, and the fileHash is enough to be sure


		System.out.println("Persist test complete!");
	}


	public void testPersistWithBoth() throws IOException {
		grepo.deleteFilePropsLocal(fileUID);
		grepo.deleteFilePropsServer(fileUID);
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		GFile newFile = new GFile(fileUID, accountUID);
		grepo.createFilePropsLocal(newFile);
		grepo.createFilePropsServer(newFile);
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Write to the stall file
		String writtenFileHash = testWrite(newFile.filehash);

		//And persist the write to the closest repo that has the file
		writeStalling.persistStalledWrite(fileUID);


		//Make sure both files still exist
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Ensure the contents were written to only the closest repo
		LFile local = lrepo.getFileProps(fileUID);
		SFile server = srepo.getFileProps(fileUID);
		assert !Objects.equals(local.filehash, server.filehash);
		assert local.filehash != null;
		assert Objects.equals(local.filehash, writtenFileHash);

		//Note: We don't delete the actual content from either repo before doing this,
		// so it isn't actually written by the stallFile write if it already exists.
		//Because of this I'm not checking, and the fileHash is enough to be sure


		System.out.println("Persist test complete!");
	}
}
