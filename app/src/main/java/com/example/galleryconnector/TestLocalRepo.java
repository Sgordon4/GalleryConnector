package com.example.galleryconnector;

import android.content.ContentResolver;
import android.net.Uri;

import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestLocalRepo {

	LocalRepo localRepo = LocalRepo.getInstance();
	UUID accountUID = UUID.randomUUID();
	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");

	private void makeTestFile() {
		try {
			if(!tempFile.getParent().toFile().isDirectory())
				Files.createDirectory(tempFile.getParent());
			if(!tempFile.toFile().exists())
				Files.createFile(tempFile);


			String testData = "This is a test text file.";

			Files.write(tempFile, testData.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void uploadToLocal() throws FileNotFoundException {
		makeTestFile();

		//Create a new empty file
		LFileEntity newFile = new LFileEntity(accountUID);
		localRepo.putFileProps(newFile);

		//Upload the test file's contents to the new file
		localRepo.putFileContents(newFile.fileuid, Uri.fromFile(tempFile.toFile()));


		//Print the new file's properties
		System.out.println(localRepo.getFileProps(newFile.fileuid));
	}


	public void testExistingFile() throws IOException {
		UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

		LFileEntity file = localRepo.getFileProps(fileUID);
		System.out.println("================================================");
		System.out.println("================================================");
		System.out.println("Printing existing file:");
		System.out.println(file);
		System.out.println("\n\n");


		List<String> blockList = file.fileblocks;
		ContentResolver contentResolver = MyApplication.getAppContext().getContentResolver();

		List<InputStream> blockStreams = new ArrayList<>();
		for(String block : blockList) {
			Uri blockUri = localRepo.getBlockUri(block);
			blockStreams.add(contentResolver.openInputStream(blockUri));
		}

		System.out.println("HERE");
		ConcatenatedInputStream concatStream = new ConcatenatedInputStream(blockStreams);
		int byteRead;
		while((byteRead = concatStream.read()) != -1) {
			System.out.print((char) byteRead);
		}
		concatStream.close();

		System.out.println("Finished");


	}
}
