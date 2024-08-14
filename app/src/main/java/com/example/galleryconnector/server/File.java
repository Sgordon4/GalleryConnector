package com.example.galleryconnector.server;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class File {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "GCon.File";


	public File(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//TODO
	public Boolean fileExists(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub");
	}

	public JsonObject getFileProps(@NonNull UUID fileUID) throws IOException {
		Log.i(TAG, String.format("\nGET FILE called with fileUID='"+fileUID+"'"));
		String url = Paths.get(baseServerUrl, "files", fileUID.toString()).toString();


		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------

	public JsonObject createFileEntry(@NonNull UUID ownerUID, boolean isDir, boolean isLink) throws IOException {
		RequestBody body = new FormBody.Builder()
				.add("owneruid", String.valueOf(ownerUID))
				.add("isdir", String.valueOf(isDir))
				.add("islink", String.valueOf(isLink))
				.build();

		Request request = new Request.Builder()
				.url(baseServerUrl +"/files/")
				.post(body)
				.build();


		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}
}
