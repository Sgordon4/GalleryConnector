package com.example.galleryconnector;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

public class UserAttributesTest {

	/*
	The Android Unix filesystem does NOT support UserDefinedFileAttributes.
	Therefore we're going to need to make another goddamn database.
	 */


	@Test
	public void testAttrExceptions(@TempDir Path tempDir) throws IOException {
		System.out.println("helloworld() testDir="+tempDir);

		Path testFile = tempDir.resolve("test.txt");
		Files.createDirectories(testFile.getParent());
		Files.createFile(testFile);

        //Before we make the test file, attempt to get user attributes from it
        UserDefinedFileAttributeView attrs = Files.getFileAttributeView(testFile, UserDefinedFileAttributeView.class);

		String attributeName = "myAttribute";
		String attributeValue = "Hello, world!";

		try {
			System.out.println("Before write, size="+attrs.size(attributeName));
			throw new RuntimeException();
		} catch (NoSuchFileException e) {
			//We good, can't check attr before written
		}

		ByteBuffer buffer = ByteBuffer.wrap(attributeValue.getBytes());
		attrs.write(attributeName, buffer);
		System.out.println("After write, size="+attrs.size(attributeName));

		ByteBuffer readBuffer = ByteBuffer.allocate(attrs.size(attributeName));
		attrs.read(attributeName, readBuffer);
		String readValue = new String(readBuffer.array());

		System.out.println("Read attribute: " + readValue);


		//attrs.size("something");
	}
}