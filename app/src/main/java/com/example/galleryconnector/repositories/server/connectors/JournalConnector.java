package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JournalConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private final OkHttpClient longpollClient;
	private static final String TAG = "Gal.SRepo.Journal";


	public JournalConnector(String baseServerUrl, OkHttpClient client, OkHttpClient longpollClient) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
		this.longpollClient = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	Gson gson = new GsonBuilder()
			.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
					Instant.parse(json.getAsString()))
			.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (instant, type, jsonSerializationContext) ->
					new JsonPrimitive(instant.toString()
					))
			.create();


	//Get all journal entries after a given journalID
	@Nullable
	public List<SJournal> getJournalEntriesAfter(int journalID) throws IOException {
		//Log.i(TAG, String.format("\nGET JOURNAL called with journalID='%s'", journalID));
		String url = Paths.get(baseServerUrl, "journal", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();

			//return journals;
			return new Gson().fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
		//If we don't get anything back from the server (no internet, server down, etc), just pretend we got nothing
		catch (SocketTimeoutException | ConnectException e) {
			return new ArrayList<>();
		}
	}


	//Get all journal entries for a given fileUID
	public List<SJournal> getJournalEntriesForFile(UUID fileUID) throws IOException {
		//Log.i(TAG, String.format("\nGET JOURNAL BY ID called with fileUID='%s'", fileUID));
		String url = Paths.get(baseServerUrl, "journal", "file", fileUID.toString()).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return gson.fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
	}


	//LONGPOLL all journal entries after a given journalID
	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws IOException {
		//Log.i(TAG, String.format("\nLONGPOLL JOURNAL called with journalID='%s'", journalID));
		String url = Paths.get(baseServerUrl, "journal", "longpoll", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = longpollClient.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return gson.fromJson(responseData, new TypeToken< List<SJournal> >(){}.getType());
		}
	}
}
