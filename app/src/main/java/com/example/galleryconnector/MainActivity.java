package com.example.galleryconnector;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.local.LocalRepo;

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



		Thread thread = new Thread(() -> {
			TestEverything everything = new TestEverything();

			//Delete both local and server files for a clean slate
			everything.removeFromLocal();
			everything.removeFromServer();


			// ----------- TESTING START -----------

			System.out.println("Instant");
			System.out.println(Instant.now());


			//everything.importToLocal();
			everything.importToServer();


			runOnUiThread(() -> {
				//Get an inputStream of the file contents, from the closest repo
				try (ConcatenatedInputStream inputStream = (ConcatenatedInputStream) everything.getFileContents()){

					System.out.println("EEEEEEEE");

					//And put the contents into our testing ImageView
					ImageView view = findViewById(R.id.image);
					view.setImageBitmap(BitmapFactory.decodeStream(inputStream));

					System.out.println("HERE");


					//TODO Currently getting a networkOnMainThreadException when using the concatStreams


					//This shit still don't work with the ModelLoaders
					/*
					Glide.with(view)
							.load(inputStream)
							.into(view);

					 */


				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});

		});

		thread.start();
	}
}