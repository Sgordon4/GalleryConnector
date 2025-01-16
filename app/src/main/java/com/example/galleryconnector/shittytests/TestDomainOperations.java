package com.example.galleryconnector.shittytests;

import android.net.Uri;

import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.domain_movement.DomainAPI;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;
import com.google.common.util.concurrent.ListenableFuture;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TestDomainOperations {
	GalleryRepo grepo = GalleryRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Path tempFileSmall = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");



	public void testWorkerCopyToServer() throws IOException {
		System.out.println("Testing DomainOps CopyToServer");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Attempt to copy to server, but BEFORE we actually make the file on local
		System.out.println("111111111111111111111111111111111111111111111111111");

		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		
		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing actually was erroneously created on local or server
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);



		//Actually make the file in local and try again
		System.out.println("222222222222222222222222222222222222222222222222222");

		grepo.createFilePropsLocal(local);
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is now on server
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy it again now that it exists in server
		System.out.println("333333333333333333333333333333333333333333333333333");

		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that nothing has changed
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToLocal() throws IOException {
		System.out.println("Testing DomainOps CopyToLocal");
		String fileHash = importToTempFile();

		//Put the contents in server
		if(!grepo.doesContentExistServer(fileHash))
			grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = (int) tempFileSmall.toFile().length();
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();


		//Attempt to copy to local, but BEFORE we actually make the file on server
		System.out.println("111111111111111111111111111111111111111111111111111");

		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing actually was erroneously created on local or server
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);




		//Actually make the file in server and try again
		System.out.println("222222222222222222222222222222222222222222222222222");
		grepo.createFilePropsServer(server);
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is now on local
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy it again now that it exists in local
		System.out.println("333333333333333333333333333333333333333333333333333");

		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that nothing has changed
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}


	//-------------------------------------------------


	public void testWorkerCopyToBoth_StartingLocal() throws IOException {
		System.out.println("Testing DomainOps CopyToBoth_StartingLocal");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that the file is on local AND the server
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy to both again and see what happens
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing changed
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToBoth_StartingServer() throws IOException {
		System.out.println("Testing DomainOps CopyToBoth_StartingServer");
		String fileHash = importToTempFile();

		//Put the contents in server
		if(!grepo.doesContentExistServer(fileHash))
			grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = (int) tempFileSmall.toFile().length();
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that the file is on local AND the server
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy to both again and see what happens
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing changed
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}



	//---------------------------------------------------------------------------------------------

	public void testWorkerMoveToServer() throws IOException {
		System.out.println("Testing DomainOps MoveToServer");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		//Ensure the file is not on server to start
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//Queue two operations to move the file to the server
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER, DomainAPI.REMOVE_FROM_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is on the server but not on local
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerMoveToLocal() throws IOException {
		System.out.println("Testing DomainOps MoveToLocal");
		String fileHash = importToTempFile();

		//Put the contents in server
		if(!grepo.doesContentExistServer(fileHash))
			grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = (int) tempFileSmall.toFile().length();
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		//Ensure the file is not on local to start
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Queue two operations to move the file to local
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is on local but not on the server
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	//---------------------------------------------------------------------------------------------


	public void testWorkerOppositeOpDoesNothing_fromLocal() throws IOException {
		System.out.println("Testing DomainOps OppositeDoesNothing_FromLocal");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		local = grepo.createFilePropsLocal(local);

		//Ensure the file is only on local
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//Queue two opposite operations
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that nothing has happened
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerOppositeOpDoesNothing_fromServer() throws IOException {
		System.out.println("Testing DomainOps OppositeDoesNothing_FromServer");
		String fileHash = importToTempFile();

		//Put the contents in server
		if(!grepo.doesContentExistServer(fileHash))
			grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = (int) tempFileSmall.toFile().length();
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		server = grepo.createFilePropsServer(server);

		//Ensure the file is only on the server
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Queue two opposite operations
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.REMOVE_FROM_LOCAL);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that nothing has happened
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}


	//---------------------------------------------------------------------------------------------

	public void testWorkerRemoveBoth_Both() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithBoth");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Create the file in local only
		local = grepo.createFilePropsLocal(local);

		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		System.out.println("111111111111111111111111111111111111111111111111111");

		//Copy the file to the server
		domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure the file exists in both repos
		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		System.out.println("222222222222222222222222222222222222222222222222222");


		try {
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			List<WorkInfo> info = workManager.getWorkInfosForUniqueWork("domain_"+fileUID).get();

			System.out.println("WorkInfo size:"+info.size());


		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		//Queue two removes
		domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that both are gone
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		System.out.println("333333333333333333333333333333333333333333333333333");

		//Queue two removes AGAIN
		domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that both are STILL gone, and that nothing was unhidden
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Local() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithLocalOnly");
		String fileHash = importToTempFile();

		//Put the contents in local
		if(!grepo.doesContentExistLocal(fileHash))
			grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = (int) tempFileSmall.toFile().length();
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Create the file in local only
		local = grepo.createFilePropsLocal(local);

		System.out.println("Asserting...");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		System.out.println("111111111111111111111111111111111111111111111111111");

		//Queue two removes
		domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that file does not exist in either repo
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Server() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithServerOnly");
		String fileHash = importToTempFile();

		//Put the contents in server
		if(!grepo.doesContentExistServer(fileHash))
			grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = (int) tempFileSmall.toFile().length();
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		//Create a file in server only
		server = grepo.createFilePropsServer(server);

		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		System.out.println("111111111111111111111111111111111111111111111111111");

		//Queue two removes
		domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { Thread.sleep(3000); }
		catch (InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that file does not exist in either repo
		System.out.println("Asserting...");
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	//---------------------------------------------------------------------------------------------



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
