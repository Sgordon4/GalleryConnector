package com.example.galleryconnector;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.example.galleryconnector.repositories.server.ServerRepo;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SRepoTest {
	Context context;

	@Before
	public void setup() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	//Junit likes to just end the tests before network requests come back. This stalls until then.
	@After
	public void runLonger() {
		try {
			new CountDownLatch(1).await(2000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Ignore
	@Test
	public void testFileUpload() {
		ServerRepo connector = new ServerRepo();

		//Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.smiley);
		Uri uri = Uri.parse("");


		JsonObject fileProps = new JsonObject();
		fileProps.addProperty("fileuid", UUID.randomUUID().toString());
		fileProps.addProperty("accountuid", UUID.randomUUID().toString());
		fileProps.addProperty("isdir", true);


		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			try {
				connector.uploadFile(fileProps, uri, context);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
