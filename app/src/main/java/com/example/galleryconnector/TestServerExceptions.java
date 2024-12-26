package com.example.galleryconnector;

import android.net.Uri;

import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.server.ServerRepo;

import java.util.UUID;

public class TestServerExceptions {
	GalleryRepo grepo = GalleryRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-2mb.jpg");




}
