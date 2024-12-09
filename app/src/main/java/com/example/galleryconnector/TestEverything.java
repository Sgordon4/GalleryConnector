package com.example.galleryconnector;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.file.LFile;


//import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TestEverything {

	GalleryRepo grepo = GalleryRepo.getInstance();
	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");

	Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "testfile.txt");



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




	public void externalUriToTestFile() {
		try {
			if(!tempFile.getParent().toFile().isDirectory())
				Files.createDirectory(tempFile.getParent());
			if(!tempFile.toFile().exists())
				Files.createFile(tempFile);


			//String testData = "This is a test text file.";
			//Files.write(tempFile, testData.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//Need Apache commons for this
		/*
		try {
			FileUtils.copyURLToFile(new URL(externalUri.toString()), tempFile.toFile());
			System.out.println("Copied to file");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		 */
	}



	public void importToLocal() {
		externalUriToTestFile();

		try {
			GFile newFile = new GFile(fileUID, accountUID);
			grepo.putFilePropsLocal(newFile).get();

			//grepo.putFileContentsLocal(fileUID, Uri.fromFile(tempFile.toFile())).get();		//Local temp file
			grepo.putFileContentsLocal(fileUID, externalUri).get();							//WebURL

			try {
				LFile file = LocalRepo.getInstance().getFileProps(fileUID);
				System.out.println(file);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}

		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		try {
			System.out.println(LocalRepo.getInstance().getFileProps(fileUID));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

	}
	public void importToServer() {
		externalUriToTestFile();

		try {
			GFile newFile = new GFile(fileUID, accountUID);
			grepo.putFilePropsServer(newFile).get();

			//grepo.putFileContentsServer(fileUID, Uri.fromFile(tempFile.toFile())).get();		//Local temp file
			grepo.putFileContentsServer(fileUID, externalUri).get();							//WebURL

		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void removeFromLocal() {
		try {
			grepo.deleteFilePropsLocal(fileUID).get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public void removeFromServer() {
		try {
			grepo.deleteFilePropsServer(fileUID).get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public InputStream getFileContents() {
		try {
			return grepo.getFileContents(fileUID).get();
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
