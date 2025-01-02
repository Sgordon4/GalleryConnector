package com.example.galleryconnector.shittytests;

import android.net.Uri;
import android.util.Pair;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.domain.DomainAPI;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestMultipart {
	GalleryRepo grepo = GalleryRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();
	ContentConnector contentConnector = srepo.contentConn;

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Uri externalUri_15MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-15mb.jpeg");
	Path tempFileSmall = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");
	Path tempFileLarge = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "largeFile.txt");



	public void useSrepoAutoupload() throws IOException {
		importToTempFile();

		srepo.uploadData("smallFile", tempFileSmall.toFile());
		srepo.uploadData("largeFile", tempFileLarge.toFile());
		System.out.println("Test complete!");
	}



	public void createAndDeleteMultipart() throws IOException {
		String fileName = "TestMultipart";

		Pair<UUID, List<Uri>> multiparts = contentConnector.initializeMultipart(fileName, 3);

		System.out.println("Multiparts recieved");
		UUID uploadID = multiparts.first;
		List<Uri> uris = multiparts.second;

		System.out.println(uploadID);
		for(Uri uri : uris) {
			System.out.println(uri);
		}

		System.out.println("Deleting multipart");
		contentConnector.cancelMultipart(fileName, uploadID);
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	public void importToTempFile() throws IOException {
		if(!tempFileSmall.toFile().exists()) {
			Files.createDirectories(tempFileSmall.getParent());
			Files.createFile(tempFileSmall);
		}
		if(!tempFileLarge.toFile().exists()) {
			Files.createDirectories(tempFileLarge.getParent());
			Files.createFile(tempFileLarge);
		}


		URL smallUrl = new URL(externalUri_1MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(smallUrl.openStream());
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFileSmall.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		}


		URL largeUrl = new URL(externalUri_15MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(largeUrl.openStream());
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFileLarge.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		}
	}
}
