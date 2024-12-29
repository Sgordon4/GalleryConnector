package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TestRepoBasics {
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");



	public void testLocalBasics() {
		try {
			//Put the blocks in to start
			LBlockHandler.BlockSet blockSet = lrepo.putData(externalUri_1MB);

			UUID fileUID = UUID.randomUUID();

			//Make a new file with the blockset data
			LFile newFile = new LFile(fileUID, accountUID);
			newFile.fileblocks = blockSet.blockList;
			newFile.filesize = blockSet.fileSize;
			newFile.filehash = blockSet.fileHash;

			newFile.changetime = Instant.now().getEpochSecond();
			newFile.modifytime = Instant.now().getEpochSecond();


			//Add something to muck things up
			newFile.fileblocks.add("FAKE");

			try {
				lrepo.putFileProps(newFile, "null", "null");
			} catch (DataNotFoundException e) {
				System.out.println("System successfully rejected the post because of the missing block");
				newFile.fileblocks.remove("FAKE");
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
			//Put the blocks in to start
			ServerRepo.BlockSet blockSet = srepo.putData(externalUri_1MB);

			UUID fileUID = UUID.randomUUID();

			//Make a new file with the blockset data
			SFile newFile = new SFile(fileUID, accountUID);
			newFile.fileblocks = blockSet.blockList;
			newFile.filesize = blockSet.fileSize;
			newFile.filehash = blockSet.fileHash;

			newFile.changetime = Instant.now().getEpochSecond();
			newFile.modifytime = Instant.now().getEpochSecond();


			//Add something to muck things up
			newFile.fileblocks.add("FAKE");

			try {
				srepo.putFileProps(newFile, "null", "null");
			} catch (DataNotFoundException e) {
				System.out.println("System successfully rejected the post because of the missing block");
				newFile.fileblocks.remove("FAKE");
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
}
