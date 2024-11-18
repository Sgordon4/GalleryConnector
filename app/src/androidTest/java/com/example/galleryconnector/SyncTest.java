package com.example.galleryconnector;

import static com.google.common.reflect.Reflection.getPackageName;

import android.content.Context;
import android.net.Uri;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.combined.sync.SyncHandler;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class SyncTest {


	UUID UUID_1 = UUID.fromString("11111111-f444-47ac-8b1a-6008054f3dcd");
	UUID UUID_2 = UUID.fromString("22222222-f444-47ac-8b1a-6008054f3dcd");
	UUID UUID_3 = UUID.fromString("33333333-f444-47ac-8b1a-6008054f3dcd");
	UUID UUID_4 = UUID.fromString("44444444-f444-47ac-8b1a-6008054f3dcd");
	UUID UUID_5 = UUID.fromString("55555555-f444-47ac-8b1a-6008054f3dcd");


	@Before
	public void setup() {
		UUID_1 = UUID.randomUUID();
		UUID_2 = UUID.randomUUID();
		UUID_3 = UUID.randomUUID();
		UUID_4 = UUID.randomUUID();
		UUID_5 = UUID.randomUUID();
	}



	@Test
	public void test() throws IOException, ExecutionException, InterruptedException {
		GalleryRepo gRepo = GalleryRepo.getInstance();
		LocalRepo lRepo = LocalRepo.getInstance();
		ServerRepo sRepo = ServerRepo.getInstance();
		SyncHandler handler = SyncHandler.getInstance();

		Context context = MyApplication.getAppContext();

		Uri url = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.smiley);




		/*
		handler.trySync();

		//Add a few files to both local and server ------------
		LFileEntity file1 = new LFileEntity(UUID_1);
		file1.isdir = true;
		lRepo.uploadFile(file1, url, context);
		sRepo.uploadFile(file1.toJson(), url, context);

		LFileEntity file2 = new LFileEntity(UUID_2);
		file2.islink = true;
		lRepo.uploadFile(file2, url, context);
		sRepo.uploadFile(file2.toJson(), url, context);

		LFileEntity file3 = new LFileEntity(UUID_3);
		file1.isdir = true;
		file3.islink = true;
		lRepo.uploadFile(file3, url, context);
		sRepo.uploadFile(file3.toJson(), url, context);

		LFileEntity file4 = new LFileEntity(UUID_4);
		file4.isdir = true;
		lRepo.uploadFile(file4, url, context);

		LFileEntity file5 = new LFileEntity(UUID_5);
		file5.islink = true;
		sRepo.uploadFile(file5.toJson(), url, context);


		//Make some changes -----------------------------------
		//For 1, change only local
		file1.isdir = false;
		lRepo.uploadFile(file1, url, context);

		//For 2, change only server
		file2.islink = false;
		sRepo.uploadFile(file2.toJson(), url, context);

		//For 3, change both in conflicting ways
		file3.isdir = false;
		lRepo.uploadFile(file3, url, context);
		file3.isdir = true;
		file3.islink = false;
		sRepo.uploadFile(file3.toJson(), url, context);




		handler.trySync();

		 */



	}
}
