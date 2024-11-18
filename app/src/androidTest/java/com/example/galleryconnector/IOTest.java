package com.example.galleryconnector;

import android.content.Context;

import com.example.galleryconnector.repositories.combined.movement.ImportExportApi;

import org.junit.Test;

public class IOTest {
	@Test
	public void test() {
		Context context = MyApplication.getAppContext();
		System.out.println("DATA DIR ------------------------------------------------");
		System.out.println(context.getDataDir());

		ImportExportApi ioApi = ImportExportApi.getInstance();

		//ioApi.queueImportFile(Uri.EMPTY,UUID.randomUUID(), UUID.randomUUID());
	}
}
