package com.example.galleryconnector.glide;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule
public class ISGlideModule extends AppGlideModule {
	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.prepend(InputStream.class, InputStream.class, new ISModelLoaderFactory());
	}


	public class ISModelLoaderFactory implements ModelLoaderFactory<InputStream, InputStream> {

		@NonNull
		@Override
		public ModelLoader<InputStream, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
			return new ISModelLoader();
		}

		@Override
		public void teardown() {

		}
	}
}
