package com.example.galleryconnector.repositories.combined.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

public class InputStreamModelLoaderFactory implements ModelLoaderFactory<InputStream, InputStream> {
	@NonNull
	@Override
	public ModelLoader<InputStream, InputStream> build(MultiModelLoaderFactory multiFactory) {
		return new InputStreamModelLoader();
	}

	@Override
	public void teardown() {}
}
