package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JournalConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "Gal.SRepo.Journal";


	public JournalConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Get all journal entries after a given journalID
	public List<JsonObject> getJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("\nGET JOURNAL called with journalID='"+journalID+"'"));
		String url = Paths.get(baseServerUrl, "journal", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< List<JsonObject> >(){}.getType());
		}
	}
}
