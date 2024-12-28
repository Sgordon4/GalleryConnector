package com.example.galleryconnector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.movement.DomainAPI;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.server.ServerRepo;


//import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class TestEverything {

	GalleryRepo grepo = GalleryRepo.getInstance();
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();
	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");
	//UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0568");	//Fake

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Uri externalUri_2MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");




	public void testServerUpdate() {
		//Make sure the file exists on local
		if(!grepo.isFileLocal(fileUID))
			importToLocal(externalUri_1MB);
		assert grepo.isFileLocal(fileUID);

		//Upload the file to server
		try {
			LFile localFile = lrepo.getFileProps(fileUID);
			GFile file = GFile.fromLocalFile(localFile);

			grepo.putFilePropsServer(file);

		} catch (ConnectException e) {
			System.out.println("ConnectException in testDomainMove");
			throw new RuntimeException(e);
		} catch (DataNotFoundException | FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}


	public void testDomainMove() {
		//Make sure the file exists on local but not on server
		try {
			if(!grepo.isFileLocal(fileUID))
				importToLocal(externalUri_1MB);
			assert grepo.isFileLocal(fileUID);

			grepo.deleteFilePropsServer(fileUID);
			assert !grepo.isFileServer(fileUID);

		} catch (ConnectException e) {
			System.out.println("ConnectException in testDomainMove");
			throw new RuntimeException(e);
		}

		//Try to copy the file to server
		grepo.copyFileToServer(fileUID);
		domainAPI.doSomething(1);
	}


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



	public void importToLocal(Uri uri) {
		GFile newFile = new GFile(fileUID, accountUID);

		try {
			System.out.println("Putting file contents into local");
			newFile = grepo.putDataLocal(newFile, uri);

			System.out.println("Putting file props into local");
			System.out.println(newFile);
			grepo.putFilePropsLocal(newFile);

		} catch (UnknownHostException e) {
			System.out.println("Houston we have a problem");
			throw new RuntimeException(e);
		} catch (DataNotFoundException e) {
			throw new RuntimeException(e);
		}


	}
	public void importToServer(Uri uri) {
		GFile newFile = new GFile(fileUID, accountUID);

		try {
			System.out.println("Putting file contents into server");
			newFile = grepo.putDataServer(newFile, uri);

			System.out.println("Putting file props into server");
			grepo.putFilePropsServer(newFile);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		} catch (DataNotFoundException e) {
			throw new RuntimeException(e);
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}
	}

	public void removeFromLocal() {
		grepo.deleteFilePropsLocal(fileUID);
	}
	public void removeFromServer() {
		try {
			grepo.deleteFilePropsServer(fileUID);
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}
	}

	public InputStream getFileContents() {
		try {
			return grepo.getFileContents(fileUID);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}
	}




	public void displayImage(ImageView view) {
		System.out.println("Getting InputStream ---------------------------------------------");

		//Grab an inputStream of the file contents from the closest repo that has it
		InputStream inputStream = getFileContents();
		Bitmap bitmap = BitmapFactory.decodeStream(inputStream);


		//And put the contents into our testing ImageView
		view.post(() -> {
			System.out.println("Setting Bitmap --------------------------------------------------");
			view.setImageBitmap(bitmap);
		});
		System.out.println("Finished displaying");
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------

	//Temp testing storage

	/*
	Thread thread = new Thread(() -> {

		//Delete both local and server files for a clean slate
		//everything.removeFromLocal();
		//everything.removeFromServer();

		//Since we don't actually persist these yet, update them here for now
		//everything.updateLocalSyncPointer();

		// ----------- TESTING START -----------

		//everything.importToLocal();
		//everything.importToServer();

		//everything.copyToServer();



		//everything.printLocalJournals();

		displayImage( findViewById(R.id.image) );
	});


	GFileUpdateObservers.GFileObservable observable = (journalID, file) -> {
		UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");


		System.out.println("Grabbing local file inside observer: ");
		try {
			System.out.println(gRepo.getFileProps(fileUID));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}

		if(file.fileuid.equals(fileUID)) {
			displayImage( findViewById(R.id.image) );

		}
	};
	//gRepo.addObserver(observable);
	 */


	/*
	displayImage( findViewById(R.id.image) );


	private void displayImage(ImageView view) {
		System.out.println("Getting InputStream ---------------------------------------------");

		//Grab an inputStream of the file contents from the closest repo that has it
		InputStream inputStream = everything.getFileContents();
		Bitmap bitmap = BitmapFactory.decodeStream(inputStream);


		//And put the contents into our testing ImageView
		ImageView view = findViewById(R.id.image);
		view.post(() -> {
			System.out.println("Setting Bitmap --------------------------------------------------");
			view.setImageBitmap(bitmap);
		});
		System.out.println("Finished displaying");
	}
	 */




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
