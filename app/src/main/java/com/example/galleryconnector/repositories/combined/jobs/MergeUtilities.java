package com.example.galleryconnector.repositories.combined.jobs;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;

public class MergeUtilities {

	private static final String storageDir = "merge";


	@NonNull
	public static byte[] mergeDirectories(@NonNull Uri file1, @NonNull Uri file2, @Nullable Uri base) {
		throw new RuntimeException("Stub!");
	}

	@NonNull
	public static byte[] mergeLinks(@NonNull Uri file1, @NonNull Uri file2, @Nullable Uri base) {
		throw new RuntimeException("Stub!");
	}

	//Files may be too large to merge like this. Probably a small text file, but maybe not.
	@NonNull
	public static byte[] mergeNormal(@NonNull Uri file1, @NonNull Uri file2, @Nullable Uri base) {
		throw new RuntimeException("Stub!");
	}




	public static Map<String, String> mergeAttributes(@NonNull Map<String, String> attr1,
				  @NonNull Map<String, String> attr2, @NonNull Map<String, String> lastSyncedAttrs) {
		throw new RuntimeException("Stub!");
	}



	//Helper method, returns fileHash
	private static byte[] writeData(File file, byte[] data) throws IOException {
		try(OutputStream out = Files.newOutputStream(file.toPath());
			DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			dos.write(data);

			//Return the fileHash calculated when we wrote the file
			return dos.getMessageDigest().digest();
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}
	private static File getMergeFile(@NonNull String fileName) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Merge files are stored in a merge subdirectory
		File tempRoot = new File(appDataDir, storageDir);

		return new File(tempRoot, fileName);
	}
}
