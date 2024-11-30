package com.example.galleryconnector.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ISDataFetcher implements DataFetcher<InputStream> {

	private final InputStream model;

	public ISDataFetcher(InputStream model) {
		this.model = new BufferedInputStream(model);
	}

	@Override
	public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
		try {
			// Provide the data as an InputStream
			callback.onDataReady(model);
		} catch (Exception e) {
			callback.onLoadFailed(e);
		}
	}

	@Override
	public void cleanup() {
		try {
			model.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void cancel() {
		//InputStreams can't really be cancelled, so no-op
	}

	@NonNull
	@Override
	public Class<InputStream> getDataClass() {
		return InputStream.class;
	}

	@NonNull
	@Override
	public DataSource getDataSource() {
		return DataSource.REMOTE;
	}
}