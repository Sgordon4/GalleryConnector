package com.example.galleryconnector.repositories.combined.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamDataFetcher implements DataFetcher<InputStream> {
	private final InputStream inputStream;

	public InputStreamDataFetcher(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	@Override
	public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
		callback.onDataReady(inputStream);
	}

	@Override
	public void cleanup() {
		try {
			inputStream.close();
		} catch (IOException ignored) {}
	}

	@Override
	public void cancel() {}

	@NonNull
	@Override
	public Class<InputStream> getDataClass() {
		return InputStream.class;
	}

	@NonNull
	@Override
	public DataSource getDataSource() {
		return DataSource.LOCAL;
	}
}
