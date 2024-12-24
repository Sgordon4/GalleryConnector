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

import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.combined.GalleryRepo;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public class MainActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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


		GalleryRepo gRepo = GalleryRepo.getInstance();

		System.out.println("\n\n\n-----------------------------------------------------");
		System.out.println("Instant: " + Instant.now());
		System.out.println(Instant.now().toEpochMilli());
		System.out.println("\n\n\n-----------------------------------------------------");


		Thread thread = new Thread(() -> {
			TestEverything everything = new TestEverything();

			//Delete both local and server files for a clean slate
			everything.removeFromLocal();
			everything.removeFromServer();


			// ----------- TESTING START -----------

			everything.importToLocal();
			//everything.importToServer();

			//Display an inputStream of the file contents, from the closest repo that has it
			//InputStream inputStream = everything.getFileContents();
			//displayImage(inputStream);


			everything.copyToServer();

		});

		thread.start();
	}




	private void displayImage(InputStream inputStream) {
		System.out.println("Getting InputStream ---------------------------------------------");

		Bitmap bitmap = BitmapFactory.decodeStream(inputStream);


		//And put the contents into our testing ImageView
		ImageView view = findViewById(R.id.image);
		view.post(() -> {
			System.out.println("Setting Bitmap --------------------------------------------------");
			view.setImageBitmap(bitmap);
		});
	}
}