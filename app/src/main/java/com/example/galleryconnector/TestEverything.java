package com.example.galleryconnector;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TestEverything {

	GalleryRepo grepo = GalleryRepo.getInstance();
	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");




	public void externalUriToTestFile() {
		try {
			if(!tempFile.getParent().toFile().isDirectory())
				Files.createDirectory(tempFile.getParent());
			if(!tempFile.toFile().exists())
				Files.createFile(tempFile);


			//String testData = "This is a test text file.";
			//Files.write(tempFile, testData.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			FileUtils.copyURLToFile(new URL(externalUri.toString()), tempFile.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	public void importToLocal() {
		externalUriToTestFile();

		try {
			GFile newFile = new GFile(fileUID, accountUID);
			grepo.putFilePropsLocal(newFile).get();

			grepo.putFileContentsLocal(fileUID, Uri.fromFile(tempFile.toFile())).get();
			//grepo.putFileContentsLocal(fileUID, externalUri).get();

			try {
				LFileEntity file = LocalRepo.getInstance().getFileProps(fileUID);
				System.out.println(file);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}

		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		try {
			System.out.println(LocalRepo.getInstance().getFileProps(fileUID));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

	}
	public void importToServer() {
		externalUriToTestFile();

		try {
			GFile newFile = new GFile(fileUID, accountUID);
			grepo.putFilePropsServer(newFile).get();

			grepo.putFileContentsServer(fileUID, Uri.fromFile(tempFile.toFile())).get();
			//grepo.putFileContentsServer(fileUID, externalUri).get();

		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void removeFromLocal() {
		try {
			grepo.deleteFilePropsLocal(fileUID).get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void removeFromServer() {
		try {
			grepo.deleteFilePropsServer(fileUID).get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public InputStream getFileContents() {
		try {
			InputStream stream = grepo.getFileContents(fileUID).get();
			return stream;
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}



	private void makeTestFile() {
		try {
			Files.createDirectory(tempFile.getParent());
			Files.createFile(tempFile);

			String testData = "This is a test text file.";

			Files.write(tempFile, testData.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void uploadFiletoLocal() throws IOException, NoSuchAlgorithmException {
		makeTestFile();

		byte[] bytes = Files.readAllBytes(tempFile);

		//Hash the block
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
		String hashString = BlockConnector.bytesToHex(hash);

		LFileEntity file = new LFileEntity(UUID.randomUUID());


	}

}
