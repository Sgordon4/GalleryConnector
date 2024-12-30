package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.UUID;

public class TestDomainOperations {
	GalleryRepo grepo = GalleryRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");


	public void testWorkerCopyToServer() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put the block data in local
		local = grepo.putDataLocal(local, externalUri_1MB);

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
	}

	public void testWorkerCopyToLocal() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);

		//Put the block data in server
		server = grepo.putDataServer(server, externalUri_1MB);

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
	}


	//-------------------------------------------------


	public void testWorkerCopyToBoth_StartingLocal() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put a file in Local so we can copy it to Server
		local = grepo.putDataLocal(local, externalUri_1MB);
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
	}

	public void testWorkerCopyToBoth_StartingServer() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);

		//Put a file in Server so we can copy it to Local
		server = grepo.putDataServer(server, externalUri_1MB);
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
	}



	//---------------------------------------------------------------------------------------------

	public void testWorkerMoveToServer() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put a file in Local
		local = grepo.putDataLocal(local, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	public void testWorkerMoveToLocal() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);

		//Put a file in server
		server = grepo.putDataServer(server, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	//---------------------------------------------------------------------------------------------


	public void testWorkerOppositeOpDoesNothing_fromLocal() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put a file in Local
		local = grepo.putDataLocal(local, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	public void testWorkerOppositeOpDoesNothing_fromServer() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);

		//Put a file in server
		server = grepo.putDataServer(server, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}


	//---------------------------------------------------------------------------------------------

	public void testWorkerRemoveBoth_Both() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put a file in Local and server
		local = grepo.putDataLocal(local, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	public void testWorkerRemoveBoth_Local() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile local = new GFile(fileUID, accountUID);

		//Put a file in just Local
		local = grepo.putDataLocal(local, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	public void testWorkerRemoveBoth_Server() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile server = new GFile(fileUID, accountUID);

		//Put a file in just server
		server = grepo.putDataServer(server, externalUri_1MB);
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

		System.out.println("Test Complete!");
	}

	//---------------------------------------------------------------------------------------------



	public void testDomainMoves() {
		UUID fileUID = UUID.randomUUID();

		//Put the blocks in to start
		GFile newFile = new GFile(fileUID, accountUID);
		try {
			newFile = grepo.putDataLocal(newFile, externalUri_1MB);
			grepo.putFilePropsLocal(newFile, "null", "null");
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		} catch (DataNotFoundException e) {
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
	}
}
