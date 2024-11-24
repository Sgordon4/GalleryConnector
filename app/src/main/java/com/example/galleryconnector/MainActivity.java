package com.example.galleryconnector;

import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.combined.GalleryRepo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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
			try {
				TestLocalRepo testLocalRepo = new TestLocalRepo();
				//testLocalRepo.makeTestFile();
				//testLocalRepo.downloadFile();
				//testLocalRepo.testFileToLocal();
				//testLocalRepo.importExternalFile();
				//testLocalRepo.testExistingFile();


				testLocalRepo.importExternalFile();



				UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");
				URL url = new URL("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");
				Uri uri = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");

				ImageView view = findViewById(R.id.image);
				//try (InputStream inputStream = url.openStream()){
				Path testPath = testLocalRepo.getTestFile();
				runOnUiThread(() -> {
					try (ConcatenatedInputStream inputStream = (ConcatenatedInputStream) GalleryRepo.getInstance().getFileContents(fileUID).get()){
					//try (InputStream inputStream = Files.newInputStream(testPath)){

						view.setImageBitmap(BitmapFactory.decodeStream(inputStream));


						//This shit still don't work with the ModelLoaders
						/*
						Glide.with(view)
								.load(inputStream)
								.into(view);

						 */


					} catch (IOException e) {
						throw new RuntimeException(e);
					} catch (ExecutionException e) {
						throw new RuntimeException(e);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}

				});

			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		thread.start();
	}
}