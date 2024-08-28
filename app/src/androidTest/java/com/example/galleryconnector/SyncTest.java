package com.example.galleryconnector;

import com.example.galleryconnector.sync.SyncHandler;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class SyncTest {
	@Test
	public void test() throws IOException, ExecutionException, InterruptedException {
		SyncHandler handler = SyncHandler.getInstance();
		//handler.trySync();

		Map<UUID, JsonObject> map = new LinkedHashMap<>();

		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();

		JsonObject aObj = new JsonObject();
		aObj.addProperty("a", 1);

		JsonObject bObj = new JsonObject();
		bObj.addProperty("b", 2);

		map.put(a, new JsonObject());
		map.put(b, new JsonObject());

		map.remove(a);
		map.put(a, aObj);

		map.remove(UUID.randomUUID());

		for(Map.Entry<UUID, JsonObject> entry : map.entrySet()) {
			System.out.println(entry.getKey() + " " + entry.getValue());
		}


		handler.trySync();

	}
}
