package com.example.galleryconnector;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.shittytests.TestDomainOperations;
import com.example.galleryconnector.shittytests.TestGlide;
import com.example.galleryconnector.shittytests.TestMultipart;
import com.example.galleryconnector.shittytests.TestRepoBasics;
import com.example.galleryconnector.shittytests.TestSyncOperations;
import com.example.galleryconnector.shittytests.TestWriteStalling;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
	GalleryRepo gRepo;

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
	}


	TestRepoBasics testRepoBasics = new TestRepoBasics();
	TestDomainOperations testDomainOps = new TestDomainOperations();
	TestSyncOperations testSyncOps = new TestSyncOperations();
	TestMultipart multipart = new TestMultipart();
	TestGlide testGlide = new TestGlide();
	TestWriteStalling testWrite = new TestWriteStalling();

	@Override
	protected void onResume() {
		super.onResume();

		gRepo = GalleryRepo.getInstance();
		//gRepo.initializeListeners();





		/*
		Thread glideThread = new Thread(() -> {
			try {
				ImageView imageView = findViewById(R.id.image);

				//testGlide.importToBothRepos();
				//testGlide.displayImage(imageView);
				testGlide.displayImageWithGlide(imageView);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		glideThread.start();
		 /**/



		Thread thread = new Thread(() -> {

			try {
				//testWrite.testFileAttributes();
				testWrite.testWrite();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}


			/*
			try {
				//multipart.createAndDeleteMultipart();
				multipart.useSrepoAutoupload();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			/**/



			/*
			testRepoBasics.testLocalBasics();
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			System.out.println("---------------------------------------------------------");
			testRepoBasics.testServerBasics();
			/**/


			/*
			try {
				//testDomainOps.testWorkerCopyToServer();
				//testDomainOps.testWorkerCopyToLocal();

				//testDomainOps.testWorkerCopyToBoth_StartingLocal();
				//testDomainOps.testWorkerCopyToBoth_StartingServer();

				//testDomainOps.testWorkerMoveToServer();
				//testDomainOps.testWorkerMoveToLocal();

				//testDomainOps.testWorkerOppositeOpDoesNothing_fromLocal();
				//testDomainOps.testWorkerOppositeOpDoesNothing_fromServer();

				//testDomainOps.testWorkerRemoveBoth_Both();
				//testDomainOps.testWorkerRemoveBoth_Local();
				testDomainOps.testWorkerRemoveBoth_Server();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			/**/


			/*
			try {
				testSyncOps.testSync();

				//testSyncOps.testWorkerLocalChange();
				//testSyncOps.testWorkerServerChange();
				//testSyncOps.testWorkerBothChange();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			/**/
		});
		thread.start();

	}
}