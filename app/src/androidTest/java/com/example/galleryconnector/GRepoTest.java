package com.example.galleryconnector;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class GRepoTest {

	@Test
	public void test() throws ExecutionException, InterruptedException {
		GalleryRepo grepo = GalleryRepo.getInstance();
		JsonObject accountProps = grepo.getAccountProps(UUID.randomUUID()).get();
		System.out.println(accountProps);
	}
}
