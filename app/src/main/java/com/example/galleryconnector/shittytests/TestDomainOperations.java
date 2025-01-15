package com.example.galleryconnector.shittytests;

import android.net.Uri;

import androidx.work.Operation;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.jobs.domain_movement.DomainAPI;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

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
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Attempt to copy to server, but before we actually make the file on local
		System.out.println("111111111111111111111111111111111111111111111111111");

		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		
		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing actually was erroneously created on server
		assert !grepo.isFileServer(fileUID);



		//Actually make the file in local and try again
		System.out.println("222222222222222222222222222222222222222222222222222");

		grepo.createFilePropsLocal(local);
		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is now on server
		assert grepo.isFileServer(fileUID);



		//Try to copy it again now that it exists in server
		System.out.println("333333333333333333333333333333333333333333333333333");

		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToLocal() throws IOException {
		System.out.println("Testing DomainOps CopyToLocal");
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

		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing actually was erroneously created on local
		assert !grepo.isFileLocal(fileUID);



		//Actually make the file in server and try again
		System.out.println("222222222222222222222222222222222222222222222222222");
		grepo.createFilePropsServer(server);
		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is now on local
		assert grepo.isFileLocal(fileUID);



		//Try to copy it again now that it exists in local
		System.out.println("333333333333333333333333333333333333333333333333333");

		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		System.out.println("TEST COMPLETE!");
	}


	//-------------------------------------------------


	public void testWorkerCopyToBoth_StartingLocal() throws IOException {
		System.out.println("Testing DomainOps CopyToBoth_StartingLocal");
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
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that the file is on local AND the server
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy to both again and see what happens
		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing changed
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerCopyToBoth_StartingServer() throws IOException {
		System.out.println("Testing DomainOps CopyToBoth_StartingServer");
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
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that the file is on local AND the server
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Try to copy to both again and see what happens
		op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure nothing changed
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}



	//---------------------------------------------------------------------------------------------

	public void testWorkerMoveToServer() throws IOException {
		System.out.println("Testing DomainOps MoveToServer");
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


		//Queue two operations to move the file to the server
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER, DomainAPI.REMOVE_FROM_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is on the server but not on local
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerMoveToLocal() throws IOException {
		System.out.println("Testing DomainOps MoveToLocal");
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


		//Queue two operations to move the file to local
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Ensure that the file is on local but not on the server
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	//---------------------------------------------------------------------------------------------


	public void testWorkerOppositeOpDoesNothing_fromLocal() throws IOException {
		System.out.println("Testing DomainOps OppositeDoesNothing_FromLocal");
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

		//Ensure the file is only on local
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//Queue two opposite operations
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that nothing has happened
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerOppositeOpDoesNothing_fromServer() throws IOException {
		System.out.println("Testing DomainOps OppositeDoesNothing_FromServer");
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

		//Ensure the file is only on the server
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);


		//Queue two opposite operations
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_LOCAL, DomainAPI.REMOVE_FROM_LOCAL);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that nothing has happened
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}


	//---------------------------------------------------------------------------------------------

	public void testWorkerRemoveBoth_Both() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithBoth");
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Create the file in local only
		local = grepo.createFilePropsLocal(local);

		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);



		//Copy the file to the server
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.COPY_TO_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure the file exists in both repos
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Queue two removes
		op = domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that both are gone
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Local() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithLocalOnly");
		String fileHash = importToTempFile();

		//Put the contents in local
		int filesize = grepo.putContentsLocal(fileHash, externalUri_1MB);

		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = fileHash;
		local.filesize = filesize;
		local.changetime = Instant.now().getEpochSecond();
		local.modifytime = Instant.now().getEpochSecond();

		//Create the file in local only
		local = grepo.createFilePropsLocal(local);

		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);


		//Queue two removes
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that file does not exist in either repo
		assert !grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		System.out.println("TEST COMPLETE!");
	}

	public void testWorkerRemoveBoth_Server() throws IOException {
		System.out.println("Testing DomainOps RemoveBoth_WithServerOnly");
		String fileHash = importToTempFile();

		//Put the contents in server
		int filesize = grepo.putContentsServer(fileHash, tempFileSmall.toFile());

		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);
		server.filehash = fileHash;
		server.filesize = filesize;
		server.changetime = Instant.now().getEpochSecond();
		server.modifytime = Instant.now().getEpochSecond();

		//Create a file in server only
		server = grepo.createFilePropsServer(server);

		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);



		//Queue two removes
		Operation op = domainAPI.enqueue(fileUID, DomainAPI.REMOVE_FROM_LOCAL, DomainAPI.REMOVE_FROM_SERVER);

		//Wait for the operation to complete
		try { op.getResult().get(); }
		catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

		//Make sure that file does not exist in either repo
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
