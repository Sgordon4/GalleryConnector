package com.example.galleryconnector.shittytests;

import android.net.Uri;
import android.util.Pair;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.domain.DomainAPI;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

public class TestDomainOperations {
	GalleryRepo grepo = GalleryRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Path tempFileSmall = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");



	public void testWorkerCopyToServer() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Attempt to copy to server, but before we actually make the file on local
		System.out.println("111111111111111111111111111111111111111111111111111");
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		assert !grepo.isFileServer(fileUID);

		//Actually make the file in local and try again
		System.out.println("222222222222222222222222222222222222222222222222222");
		local = grepo.createFilePropsLocal(local);
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(20);


		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on server
		assert grepo.isFileServer(fileUID);

		//Try to copy it again now that it exists in server
		System.out.println("333333333333333333333333333333333333333333333333333");
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(20);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToLocal() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();


		//Attempt to copy to local, but before we actually make the file on server
		System.out.println("111111111111111111111111111111111111111111111111111");
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		assert !grepo.isFileLocal(fileUID);

		//Actually make the file in server and try again
		System.out.println("222222222222222222222222222222222222222222222222222");
		server = grepo.createFilePropsServer(server);
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);


		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on local
		assert grepo.isFileLocal(fileUID);

		//Try to copy it again now that it exists in local
		System.out.println("333333333333333333333333333333333333333333333333333");
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);

		System.out.println("TEST COMPLETE!");
	}


	//-------------------------------------------------


	public void testWorkerCopyToBoth_StartingLocal() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on local AND the server
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		//Try to copy to both and see what happens
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToBoth_StartingServer() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on local AND the server
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		//Try to copy to both and see what happens
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		grepo.queueCopyFileToLocal(fileUID);
		domainAPI.doSomething(20);

		System.out.println("TEST COMPLETE!");
	}



	//---------------------------------------------------------------------------------------------

	public void testWorkerMoveToServer() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//And queue two operations to move it to the server
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		grepo.queueRemoveFileFromLocal(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on local AND the server
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerMoveToLocal() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//And queue two operations to move it to local
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Now that the file is on local AND the server
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	//---------------------------------------------------------------------------------------------


	public void testWorkerOppositeOpDoesNothing_fromLocal() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//And queue two opposite operations
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Make sure that nothing has happened
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerOppositeOpDoesNothing_fromServer() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//And queue two opposite operations
		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToLocal(fileUID);
		grepo.queueRemoveFileFromLocal(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Make sure that nothing has happened
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}


	//---------------------------------------------------------------------------------------------

	public void testWorkerRemoveBoth_Both() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		domainAPI.clearQueuedItems();
		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//And queue two removes
		domainAPI.clearQueuedItems();
		grepo.queueRemoveFileFromLocal(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Make sure that both are gone
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Local() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//And queue two removes
		domainAPI.clearQueuedItems();
		grepo.queueRemoveFileFromLocal(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Make sure that both are gone
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Server() throws IOException {
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//And queue two removes
		domainAPI.clearQueuedItems();
		grepo.queueRemoveFileFromLocal(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(20);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Make sure that both are gone
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	//---------------------------------------------------------------------------------------------



	public void testDomainMoves() {
		UUID fileUID = UUID.randomUUID();

		//Put the contents in to start
		GFile newFile = new GFile(fileUID, accountUID);
		try {
			String fileHash = importToTempFile();

			//Put the contents in local
			int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

			newFile.filehash = fileHash;
			newFile.filesize = filesize;
			newFile.changetime = Instant.now().getEpochSecond();
			newFile.modifytime = Instant.now().getEpochSecond();

			grepo.putFilePropsLocal(newFile, "null", "null");
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		} catch (ContentsNotFoundException e) {
			throw new RuntimeException(e);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		grepo.queueCopyFileToServer(fileUID);
		domainAPI.doSomething(5);

		//Wait a sec for the worker to do things
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		//Now remove from local so we can put it back later
		grepo.queueRemoveFileFromLocal(fileUID);
		domainAPI.doSomething(5);


		//Wait a sec for the worker to do things
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		//Now go the other way

		grepo.queueCopyFileToLocal(fileUID);
		grepo.queueRemoveFileFromServer(fileUID);
		domainAPI.doSomething(5);

		System.out.println("TEST COMPLETE!");
	}





	//Returns filehash
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
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
