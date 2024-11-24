package com.example.galleryconnector;

import android.content.ContentResolver;
import android.net.Uri;

import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;

import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestLocalRepo {

	LocalRepo localRepo = LocalRepo.getInstance();
	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	String externalFile = "https://sample-videos.com/img/Sample-jpg-image-2mb.jpg";

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");

	public void makeTestFile() {
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

	public void downloadFile() {

		try {
			URL url = new URL(externalFile);
			FileUtils.copyURLToFile(url, tempFile.toFile());

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Path getTestFile() {
		return tempFile;
	}


	public void testFileToLocal() throws FileNotFoundException {
		//Create base file props
		LFileEntity newFile = new LFileEntity(fileUID, accountUID);
		localRepo.putFileProps(newFile);

		//Upload the test file's contents to the new file
		localRepo.putFileContents(newFile.fileuid, Uri.fromFile(tempFile.toFile()));



		//Print the new file's properties
		System.out.println(localRepo.getFileProps(newFile.fileuid));
	}

	public void importExternalFile() throws FileNotFoundException {
		LFileEntity file = new LFileEntity(fileUID, accountUID);

		localRepo.putFileContents(file.fileuid, Uri.decode(externalFile));
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
			//Now that we're testing multi megabyte images, don't print those...
			//System.out.print((char) byteRead);
		}
		concatStream.close();

		System.out.println("Finished");


	}
}
