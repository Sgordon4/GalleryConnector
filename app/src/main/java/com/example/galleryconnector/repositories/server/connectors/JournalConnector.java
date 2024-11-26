package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

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
	public List<SJournal> getJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("\nGET JOURNAL called with journalID='%s'", journalID));
		String url = Paths.get(baseServerUrl, "journal", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
	}


	//Get all journal entries for a given fileUID
	public List<SJournal> getJournalEntriesForFile(UUID fileUID) throws IOException {
		Log.i(TAG, String.format("\nGET JOURNAL BY ID called with fileUID='%s'", fileUID));
		String url = Paths.get(baseServerUrl, "journal", "file", fileUID.toString()).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
	}


	//LONGPOLL all journal entries after a given journalID
	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("\nLONGPOLL JOURNAL called with journalID='%s'", journalID));
		String url = Paths.get(baseServerUrl, "journal", "longpoll", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
	}
}
