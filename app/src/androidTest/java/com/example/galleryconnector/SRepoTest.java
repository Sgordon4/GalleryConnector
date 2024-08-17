package com.example.galleryconnector;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.example.galleryconnector.server.ServerConnector;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SRepoTest {
	Context context;

	@Before
	public void setup() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	@Test
	public void testFileUpload() {
		ServerConnector connector = new ServerConnector();

		Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/"
				+ R.raw.smiley);


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
