package com.example.galleryconnector.server;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Account {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "GCon.Account";


	public Account(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Get the account data from the server database
	public JsonObject getAccountProps(@NonNull UUID accountID) throws IOException {
		Log.i(TAG, String.format("\nGET ACCOUNT called with accountID='"+accountID+"'"));
		String url = Paths.get(baseServerUrl, "accounts", accountID.toString()).toString();

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
}
