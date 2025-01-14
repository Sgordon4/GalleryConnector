package com.example.galleryconnector.shittytests;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.jobs.writestalling.WriteStalling;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class TestWriteStalling {
	GalleryRepo grepo = GalleryRepo.getInstance();
	WriteStalling writeStalling = WriteStalling.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	String sampleData = "Sample";




	public void testWrite() throws IOException {
		List<UUID> stallFiles = writeStalling.listStallFiles();
		for(UUID uuid : stallFiles) {
			writeStalling.delete(uuid);
		}
		stallFiles = writeStalling.listStallFiles();
		assert stallFiles.isEmpty();


		String fileHash = writeStalling.write(fileUID, sampleData.getBytes(), "null");
		System.out.println("FileHash: " + fileHash);

		stallFiles = writeStalling.listStallFiles();
		assert stallFiles.size() == 1;

		File stallFile = writeStalling.getStallFile(fileUID);

		try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(stallFile.toPath()))) {
			System.out.println("Reading file");
			byte[] buffer = new byte[(int) stallFile.length()];
			in.read(buffer);
			System.out.println(new String(buffer));

			assert buffer.length == sampleData.length();
			assert new String(buffer).equals(sampleData);
		}
	}


	public void testPersist() {
		writeStalling.persistStalledWrite(fileUID);
	}
}
