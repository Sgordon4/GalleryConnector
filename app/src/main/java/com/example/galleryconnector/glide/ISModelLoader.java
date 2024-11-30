package com.example.galleryconnector.glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

public class ISModelLoader implements ModelLoader<InputStream, InputStream> {

	@Nullable
	@Override
	public LoadData<InputStream> buildLoadData(@NonNull InputStream inputStream, int width, int height, @NonNull Options options) {
		//TODO We need an actual cache item instead of just the inputStream
		return new LoadData<>(new ObjectKey(inputStream), new ISDataFetcher(inputStream));
	}

	@Override
	public boolean handles(@NonNull InputStream inputStream) {
		return true;
	}
}