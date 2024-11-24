package com.example.galleryconnector.repositories.combined.glide;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

public class InputStreamModelLoader implements ModelLoader<InputStream, InputStream> {
	@Nullable
	@Override
	public LoadData<InputStream> buildLoadData(InputStream model, int width, int height, Options options) {
		return new LoadData<>(new ObjectKey(model), new InputStreamDataFetcher(model));
	}

	@Override
	public boolean handles(InputStream model) {
		return true; // Indicates it can handle InputStream
	}
}
