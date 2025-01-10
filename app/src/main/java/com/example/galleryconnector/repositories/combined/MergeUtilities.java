package com.example.galleryconnector.repositories.combined;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Map;

public class MergeUtilities {


	public static byte[] mergeDirectories(@NonNull Uri file1, @NonNull Uri file2,
										@Nullable Uri commonBase) {
		throw new RuntimeException("Stub!");
	}
	public static void mergeDirectories(@NonNull Uri file1, @NonNull Uri file2,
										@Nullable Uri commonBase,
										@NonNull File destination) {
		throw new RuntimeException("Stub!");
	}


	public static Map<String, String> mergeAttributes(@NonNull Map<String, String> attr1,
													  @NonNull Map<String, String> attr2,
													  @NonNull Map<String, String> lastSyncedAttrs) {
		throw new RuntimeException("Stub!");
	}
}
