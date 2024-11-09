package com.example.galleryconnector;

import com.example.galleryconnector.repositories.local.file.LFileEntity;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class TestEverything {

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");

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
		file.fileblocks


	}

}
