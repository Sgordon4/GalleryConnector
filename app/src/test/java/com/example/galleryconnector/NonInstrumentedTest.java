package com.example.galleryconnector;

import org.junit.Test;

import com.example.galleryconnector.repositories.local.file.LFile;

import java.time.Instant;
import java.util.UUID;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class NonInstrumentedTest {
	@Test
	public void Test() {
		LFile entity = new LFile(UUID.randomUUID());

		System.out.println("Gson");
		System.out.println(entity.toJson());
		System.out.println(entity);

		entity.isdir = true;
		System.out.println(entity.toJson());
		System.out.println(entity);
	}


	@Test
	public void testInstantTruncate() {
		Instant now = Instant.now();
		System.out.println(now);
		System.out.println(now.toEpochMilli());

		Instant truncated = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		System.out.println(truncated);
		System.out.println(truncated.toEpochMilli());
		System.out.println(truncated.toEpochMilli()/1000);
		System.out.println(now.getEpochSecond());
	}
}