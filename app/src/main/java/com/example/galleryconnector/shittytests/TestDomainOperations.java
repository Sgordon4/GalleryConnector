package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;

import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

public class TestDomainOperations {
	GalleryRepo grepo = GalleryRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");



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

		grepo.copyFileToServer(fileUID);
		domainAPI.doSomething(5);

		//Wait a sec for the worker to do things
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		//Now remove from local so we can put it back later
		grepo.removeFileFromLocal(fileUID);
		domainAPI.doSomething(5);


		//Wait a sec for the worker to do things
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		//Now go the other way

		grepo.copyFileToLocal(fileUID);
		grepo.removeFileFromServer(fileUID);
		domainAPI.doSomething(5);
	}
}
