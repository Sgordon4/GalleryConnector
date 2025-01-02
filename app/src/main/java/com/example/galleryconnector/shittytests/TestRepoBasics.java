package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ContentsNotFoundException;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.content.LContent;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;
import com.example.galleryconnector.repositories.server.servertypes.SContent;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TestRepoBasics {
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Path tempFileSmall = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");



	public void testLocalBasics() {
		try {
			//Put the data in to start
			String fileHash = importToTempFile();
			LContent contentProps = lrepo.writeContents(fileHash, Uri.fromFile(tempFileSmall.toFile()));

			UUID fileUID = UUID.randomUUID();

			//Make a new file with the data properties
			LFile newFile = new LFile(fileUID, accountUID);
			newFile.filehash = contentProps.name;
			newFile.filesize = contentProps.size;

			newFile.changetime = Instant.now().getEpochSecond();
			newFile.modifytime = Instant.now().getEpochSecond();


			//Change the filehash to one that doesn't exist
			newFile.filehash = "FAKE";

			try {
				lrepo.putFileProps(newFile, "null", "null");
			} catch (ContentsNotFoundException e) {
				System.out.println("System successfully rejected the post because of the missing content");
				newFile.filehash = fileHash;
			}
			lrepo.putFileProps(newFile, "null", "null");


			LFile file = lrepo.getFileProps(fileUID);
			System.out.println(file.toJson());

			lrepo.deleteFileProps(fileUID);

			try {
				System.out.println("Do we have a bingo?");
				lrepo.getFileProps(fileUID);
			} catch (FileNotFoundException e) {
				System.out.println("Bingo");
			}


			file.userattr.addProperty("TestProp", "TestValue");
			file.changetime = Instant.now().getEpochSecond();

			file = lrepo.putFileProps(file, file.filehash, file.attrhash);
			System.out.println(file);


			List<LJournal> journalEntries = lrepo.getJournalEntriesForFile(fileUID);
			System.out.println("Journal entries: ");
			for(LJournal entry : journalEntries) {
				System.out.println(entry.toString());
			}


		}  catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void testServerBasics() {
		try {
			//Put the data in to start
			String fileHash = importToTempFile();
			SContent contentProps = srepo.uploadData(fileHash, tempFileSmall.toFile());

			UUID fileUID = UUID.randomUUID();

			//Make a new file with the file properties
			SFile newFile = new SFile(fileUID, accountUID);
			newFile.filehash = contentProps.name;
			newFile.filesize = contentProps.size;

			newFile.changetime = Instant.now().getEpochSecond();
			newFile.modifytime = Instant.now().getEpochSecond();


			//Change the filehash to one that doesn't exist
			newFile.filehash = "FAKE";

			try {
				srepo.putFileProps(newFile, "null", "null");
			} catch (ContentsNotFoundException e) {
				System.out.println("System successfully rejected the post because of the missing content");
				newFile.filehash = fileHash;
			}
			srepo.putFileProps(newFile, "null", "null");


			SFile file = srepo.getFileProps(fileUID);
			System.out.println(file.toJson());

			srepo.deleteFileProps(fileUID);

			try {
				System.out.println("Do we have a bingo?");
				srepo.getFileProps(fileUID);
			} catch (FileNotFoundException e) {
				System.out.println("Bingo");
			}


			file.userattr.addProperty("TestProp", "TestValue");
			file.changetime = Instant.now().getEpochSecond();

			file = srepo.putFileProps(file, file.filehash, file.attrhash);
			System.out.println(file);


			List<SJournal> journalEntries = srepo.getJournalEntriesForFile(fileUID);
			System.out.println("Journal entries: ");
			for(SJournal entry : journalEntries) {
				System.out.println(entry.toString());
			}


		}  catch (IOException e) {
			throw new RuntimeException(e);
		}
	}




	private String importToTempFile() throws IOException {
		if(!tempFileSmall.toFile().exists()) {
			Files.createDirectories(tempFileSmall.getParent());
			Files.createFile(tempFileSmall);
		}

		URL largeUrl = new URL(externalUri_1MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(largeUrl.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFileSmall.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}


			return ContentConnector.bytesToHex( dis.getMessageDigest().digest() );
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
