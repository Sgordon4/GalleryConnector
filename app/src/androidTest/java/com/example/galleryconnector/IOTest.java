package com.example.galleryconnector;

import android.content.Context;
import android.net.Uri;

import com.example.galleryconnector.movement.FileIOApi;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class IOTest {
	@Test
	public void test() {
		Context context = MyApplication.getAppContext();
		System.out.println("DATA DIR ------------------------------------------------");
		System.out.println(context.getDataDir());

		FileIOApi ioApi = FileIOApi.getInstance();

		//ioApi.queueImportFile(Uri.EMPTY,UUID.randomUUID(), UUID.randomUUID());
	}
}
