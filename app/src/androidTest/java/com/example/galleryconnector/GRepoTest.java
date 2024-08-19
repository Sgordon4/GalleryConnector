package com.example.galleryconnector;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GRepoTest {

	//Junit likes to just end the tests before network requests come back. This stalls until then.
	@Ignore
	@After
	public void runLonger() {
		try {
			new CountDownLatch(1).await(2000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void test() throws ExecutionException, InterruptedException {
		GalleryRepo grepo = GalleryRepo.getInstance();
		JsonObject accountProps = grepo.getAccountProps(UUID.fromString("d27959e2-a692-4954-a6e8-d0be91bf4489")).get();
		System.out.println(accountProps);
	}
}
