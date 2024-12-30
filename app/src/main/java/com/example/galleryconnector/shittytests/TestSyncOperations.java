package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.combined.sync.SyncHandler;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.servertypes.SFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class TestSyncOperations {
	GalleryRepo grepo = GalleryRepo.getInstance();
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	SyncHandler syncHandler = SyncHandler.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");



	public void testSync() throws IOException {
		UUID fileUID = UUID.randomUUID();
		GFile file = new GFile(fileUID, accountUID);

		System.out.println("----------------------------------------------------------------");
		System.out.println("1111111111111111111111111111111111111111111111111111111111111111");
		System.out.println("----------------------------------------------------------------");

		//Start with a file in local
		file = grepo.putDataLocal(file, externalUri_1MB);
		file = grepo.putFilePropsLocal(file, "null", "null");
		assert grepo.isFileLocal(fileUID);
		assert !grepo.isFileServer(fileUID);

		//And try to one-sided sync. Should do nothing
		syncHandler.trySync(fileUID);

		//------------------------------------------------

		System.out.println("----------------------------------------------------------------");
		System.out.println("2222222222222222222222222222222222222222222222222222222222222222");
		System.out.println("----------------------------------------------------------------");

		//Copy the file to the server to test syncing with identical data
		domainAPI.copyFileToServer(file.toLocalFile(), "null", "null");
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		//And try to sync. Since data is the same, should do nothing
		syncHandler.trySync(fileUID);

		//------------------------------------------------

		System.out.println("----------------------------------------------------------------");
		System.out.println("3333333333333333333333333333333333333333333333333333333333333333");
		System.out.println("----------------------------------------------------------------");

		//Remove the file from local to test syncing one-sided from server
		domainAPI.removeFileFromLocal(fileUID);
		assert !grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		//And try to one-sided sync. Should do nothing
		syncHandler.trySync(fileUID);

		//------------------------------------------------

		System.out.println("----------------------------------------------------------------");
		System.out.println("4444444444444444444444444444444444444444444444444444444444444444");
		System.out.println("----------------------------------------------------------------");


		//Put the file back in local
		domainAPI.copyFileToLocal(file.toServerFile(), "null", "null");

		//And make some changes to the file
		file.userattr.addProperty("TESTATTR", "TESTVALUE");
		file.changetime = Instant.now().getEpochSecond();
		file = grepo.putFilePropsLocal(file, file.filehash, file.attrhash);
		assert grepo.isFileLocal(fileUID);
		assert grepo.isFileServer(fileUID);

		//And try to sync. This should sync data L -> S
		syncHandler.trySync(fileUID);

		//Check that data was synced
		LFile lFile = lrepo.getFileProps(fileUID);
		assert Objects.equals(file.filehash, lFile.filehash);
		assert Objects.equals(file.attrhash, lFile.attrhash);

		SFile sFile = srepo.getFileProps(fileUID);
		assert Objects.equals(file.filehash, sFile.filehash);
		assert Objects.equals(file.attrhash, sFile.attrhash);

		//------------------------------------------------

		System.out.println("----------------------------------------------------------------");
		System.out.println("5555555555555555555555555555555555555555555555555555555555555555");
		System.out.println("----------------------------------------------------------------");

		//Update the file on the server
		file.userattr.addProperty("CanIGetAn", "OWAOWA");
		file.changetime = Instant.now().getEpochSecond();
		file = grepo.putFilePropsServer(file, file.filehash, file.attrhash);

		//And try to sync. This should sync data S -> L
		syncHandler.trySync(fileUID);

		//Check that data was synced
		lFile = lrepo.getFileProps(fileUID);
		assert Objects.equals(file.filehash, lFile.filehash);
		assert Objects.equals(file.attrhash, lFile.attrhash);

		sFile = srepo.getFileProps(fileUID);
		assert Objects.equals(file.filehash, sFile.filehash);
		assert Objects.equals(file.attrhash, sFile.attrhash);

		//------------------------------------------------

		System.out.println("----------------------------------------------------------------");
		System.out.println("6666666666666666666666666666666666666666666666666666666666666666");
		System.out.println("----------------------------------------------------------------");

		//Now change both files separately

		//Update the file on local
		lFile.userattr.addProperty("LocalProp", "SomethingLocal");
		lFile.changetime = Instant.now().getEpochSecond();
		lFile = lrepo.putFileProps(lFile, lFile.filehash, lFile.attrhash);

		//And update the file on the server
		sFile.userattr.addProperty("ServerProp", "Far, far away");
		sFile.changetime = Instant.now().getEpochSecond();
		sFile = srepo.putFileProps(sFile, sFile.filehash, sFile.attrhash);


		//And try to sync. This should last writer wins, prob S -> L
		syncHandler.trySync(fileUID);

		//Check that data was synced
		lFile = lrepo.getFileProps(fileUID);
		sFile = srepo.getFileProps(fileUID);
		assert Objects.equals(lFile.filehash, sFile.filehash);
		assert Objects.equals(lFile.attrhash, sFile.attrhash);

	}
}
