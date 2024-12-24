package com.example.galleryconnector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.galleryconnector.repositories.combined.GFileUpdateObservers;
import com.example.galleryconnector.repositories.combined.GalleryRepo;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
	GalleryRepo gRepo;
	TestEverything everything = new TestEverything();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		gRepo = GalleryRepo.getInstance();

		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});


		//Plan: Make a test text file, and import it to local repo
		//Gotta get a way to just upload a block though. Don't have that yet.
		//Then we just put file once the blocks are uploaded


		Thread thread = new Thread(() -> {

			//Delete both local and server files for a clean slate
			everything.removeFromLocal();
			everything.removeFromServer();

			//Since we don't actually persist these yet, update them here for now
			everything.updateLocalSyncPointer();

			// ----------- TESTING START -----------

			everything.importToLocal();
			//everything.importToServer();

			//everything.copyToServer();



			//everything.printLocalJournals();
		});

		thread.start();

		GFileUpdateObservers.GFileObservable observable = (journalID, file) -> {
			UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

			System.out.println("Grabbing local file inside observer: ");
			try {
				System.out.println(gRepo.getFileProps(fileUID).get());
			} catch (ExecutionException | InterruptedException e) {
				throw new RuntimeException(e);
			}

			if(file.fileuid.equals(fileUID)) {
				//displayImage();
			}
		};
		gRepo.addObserver(observable);
	}




	private void displayImage() {
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
}