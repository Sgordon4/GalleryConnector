package com.example.galleryconnector;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;


//import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TestEverything {

	GalleryRepo grepo = GalleryRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();
	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	//UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0568");	//Fake

	Uri externalUri = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");




	public void copyToLocal() {
		grepo.copyFileToLocal(fileUID);
		domainAPI.doSomething(1);
	}
	public void copyToServer() {
		grepo.copyFileToServer(fileUID);
		domainAPI.doSomething(1);
	}


	public void updateLocalSyncPointer() {
		//Update the sync pointer since we don't persist it yet...
		List<LJournal> journals = LocalRepo.getInstance().getJournalEntriesAfter(0);
		System.out.println("NumJournals: ");
		System.out.println(journals.size());
	}



	public void importToLocal() {
		GFile newFile = new GFile(fileUID, accountUID);

		try {
			System.out.println("Putting file contents into local");
			newFile = grepo.putDataLocal(newFile, externalUri);				//WebURL

			System.out.println("Putting file props into local");
			System.out.println(newFile);
			grepo.putFilePropsLocal(newFile);

		} catch (UnknownHostException e) {
			System.out.println("Houston we have a problem");
			throw new RuntimeException(e);
		}


	}
	public void importToServer() {
		GFile newFile = new GFile(fileUID, accountUID);

		System.out.println("Putting file contents into server");
		//externalUriToTestFile();
		//grepo.putFileContentsServer(fileUID, Uri.fromFile(tempFile.toFile())).get();		//Local temp file
		newFile = grepo.putDataServer(newFile, externalUri);				//WebURL

		System.out.println("Putting file props into server");
		grepo.putFilePropsServer(newFile);
	}

	public void removeFromLocal() {
		grepo.deleteFilePropsLocal(fileUID);
	}
	public void removeFromServer() {
		grepo.deleteFilePropsServer(fileUID);
	}

	public InputStream getFileContents() {
		try {
			return grepo.getFileContents(fileUID);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}





	//Example show image in View from MainActivity testing
	/*
	Thread thread = new Thread(() -> {
			TestEverything everything = new TestEverything();

			//Delete both local and server files for a clean slate
			everything.removeFromLocal();
			everything.removeFromServer();


			// ----------- TESTING START -----------

			//everything.importToLocal();
			everything.importToServer();


			System.out.println("Getting InputStream ---------------------------------------------");
			//Get an inputStream of the file contents, from the closest repo that has it
			InputStream inputStream = everything.getFileContents();

			Bitmap bitmap = BitmapFactory.decodeStream(inputStream);


			//And put the contents into our testing ImageView
			ImageView view = findViewById(R.id.image);
			view.post(() -> {
				System.out.println("Setting Bitmap --------------------------------------------------");
				view.setImageBitmap(bitmap);
			});

		});

		thread.start();
	*/
}
