package com.example.galleryconnector.shittytests;

import android.net.Uri;
import android.util.Pair;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.domain.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TestMultipart {
	GalleryRepo grepo = GalleryRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();
	BlockConnector blockConnector = srepo.blockConn;

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Uri getExternalUri_15MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-15mb.jpeg");
	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");


	public void importToTempFile() throws IOException {
		if(!tempFile.toFile().exists()) {
			Files.createDirectories(tempFile.getParent());
			Files.createFile(tempFile);
		}

		URL url = new URL(getExternalUri_15MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(url.openStream());
			FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		}
	}


	public void completeMultipart() throws IOException {
		String fileName = "TestMultipart";
		UUID uploadID = UUID.fromString("01000194-1cc3-cd1b-2841-c4ecb45e1602");

		List<BlockConnector.ETag> etags = new ArrayList<>();
		etags.add(new BlockConnector.ETag(1, "C2BC86A8E2619049F5660A2EB5872AF6BE017688AB86D3063F7E6B10EC3EF6B1"));
		etags.add(new BlockConnector.ETag(2, "60B8E667D206012055A1404FD0EA3EB65A1DCF3F615CFAD9887FB28366AECC1A"));
		etags.add(new BlockConnector.ETag(3, "2AC9E6D1B1C1BD1731D1300C999902D29550A827026259D2607BD4553645F1C2"));


		blockConnector.completeMultipart(fileName, uploadID, etags);
	}


	//Multipart should be used if the file is > 5MB
	public void uploadToMultipart() throws IOException {
		//tempFile.toFile().delete();


		if(!tempFile.toFile().exists())
			importToTempFile();

		int fileSize = (int) tempFile.toFile().length();
		System.out.println("FileSize is "+fileSize);

		int MIN_PART_SIZE = 1024 * 1024 * 5;  //5MB
		int numParts = fileSize / MIN_PART_SIZE;
		if(fileSize % MIN_PART_SIZE != 0)
			numParts++;


		String fileName = "TestMultipart";

		Pair<UUID, List<Uri>> multiparts = blockConnector.initializeMultipart(fileName, numParts);
		System.out.println("Multiparts recieved");
		UUID uploadID = multiparts.first;
		List<Uri> uris = multiparts.second;




		List<BlockConnector.ETag> etags = new ArrayList<>();
		try (BufferedInputStream in = new BufferedInputStream( Files.newInputStream(tempFile) );
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {

			//If this code is used in parallel, each one uses 5MB of memory for the buffer
			for(int i = 0; i < uris.size(); i++) {
				int remaining = fileSize - (MIN_PART_SIZE * i);
				int partSize = Math.min(MIN_PART_SIZE, remaining);

				byte[] buffer = new byte[partSize];
				int bytesRead = dis.read(buffer, 0, partSize);
				if(bytesRead != partSize)
					throw new RuntimeException();

				System.out.println("Writing "+bytesRead+" bytes");
				String uri = uris.get(i).toString();
				String ETag = blockConnector.uploadToMultipartUrl(buffer, uri);

				etags.add(new BlockConnector.ETag(i+1, ETag));
			}
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		System.out.println("Uploads complete");
		System.out.println("Etags: ");
		System.out.println(Arrays.toString(etags.toArray()));




		blockConnector.completeMultipart(fileName, uploadID, etags);
	}



	public void createAndDeleteMultipart() throws IOException {
		String fileName = "TestMultipart";

		Pair<UUID, List<Uri>> multiparts = blockConnector.initializeMultipart(fileName, 3);

		System.out.println("Multiparts recieved");
		UUID uploadID = multiparts.first;
		List<Uri> uris = multiparts.second;

		System.out.println(uploadID);
		for(Uri uri : uris) {
			System.out.println(uri);
		}

		System.out.println("Deleting multipart");
		blockConnector.deleteMultipart(fileName, uploadID);
	}
}
