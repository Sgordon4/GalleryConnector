package com.example.galleryconnector;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.galleryconnector.local.file.LFileEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class NonInstrumentedTest {
	@Test
	public void Test() {
		LFileEntity entity = new LFileEntity(UUID.randomUUID());

		System.out.println("Gson");
		System.out.println(entity.toJson());
		System.out.println(entity);

		entity.isdir = true;
		System.out.println(entity.toJson());
		System.out.println(entity);
	}
}