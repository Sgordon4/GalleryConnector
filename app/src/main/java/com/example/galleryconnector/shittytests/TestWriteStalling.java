package com.example.galleryconnector.shittytests;

import android.net.Uri;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.WriteStalling;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TestWriteStalling {
	GalleryRepo grepo = GalleryRepo.getInstance();
	WriteStalling writeStalling = WriteStalling.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	byte[] sampleData = {'S', 'A', 'M', 'P', 'L', 'E'};




	public void testWrite() throws IOException {
		List<UUID> stallFiles = writeStalling.listStallFiles();
		for(UUID uuid : stallFiles) {
			writeStalling.delete(uuid);
		}
		stallFiles = writeStalling.listStallFiles();
		assert stallFiles.isEmpty();


		String fileHash = writeStalling.write(fileUID, sampleData, "null");

		System.out.println("FileHash: " + fileHash);

		stallFiles = writeStalling.listStallFiles();
		assert stallFiles.size() == 1;

		File stallFile = writeStalling.getStallFile(fileUID);
		byte[] stallFileData = Files.readAllBytes(stallFile.toPath());
		System.out.println(ByteBuffer.wrap(stallFileData));
		assert stallFileData.length == sampleData.length;
		assert ByteBuffer.wrap(stallFileData).toString().equals(ByteBuffer.wrap(sampleData).toString());
	}

}
