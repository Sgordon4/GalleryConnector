package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "Gal.SRepo.File";

	//For reference
	private static final String[] fileProps = {
			"fileuid",
			"accountuid",

			"isdir",
			"islink",
			"isdeleted",
			"ishidden",
			"userattr",

			"fileblocks",
			"filesize",
			"filehash",

			"changetime",
			"modifytime",
			"accesstime",
			"createtime",

			"attrhash"
	};


	public FileConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//TODO No endpoint for this one yet. I'll probably just keep using (getProps() != null).
	public Boolean exists(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub");
	}

	public SFile getProps(@NonNull UUID fileUID) throws IOException {
		//Log.i(TAG, String.format("\nGET FILE called with fileUID='"+fileUID+"'"));
		String url = Paths.get(baseServerUrl, "files", fileUID.toString()).toString();


		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();

			//TODO Remove this logging
			JsonObject responseJson = JsonParser.parseString(responseData).getAsJsonObject();
			Log.d(TAG, "Response: "+responseJson.toString());

			Gson gson = new GsonBuilder()
					.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
							Instant.parse(json.getAsString()))
					.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (instant, type, jsonSerializationContext) ->
							new JsonPrimitive(instant.toString()
							))
					.create();

			return gson.fromJson(responseData.trim(), SFile.class);
			//return new Gson().fromJson(responseData.trim(), SFile.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create or update a file entry in the database
	public boolean upsert(@NonNull SFile file, @Nullable String prevFileHash, @Nullable String prevAttrHash) throws IOException {
		//Log.i(TAG, "\nUPSERT FILE called");

		//Alongside the usual url, send fileHash and attrHash as query params if applicable
		String base = Paths.get(baseServerUrl, "files", "upsert").toString();
		HttpUrl.Builder httpBuilder = HttpUrl.parse(base).newBuilder();

		if(prevFileHash != null)
			httpBuilder.addQueryParameter("prevfilehash", prevFileHash);
		if(prevAttrHash != null)
			httpBuilder.addQueryParameter("prevattrhash", prevAttrHash);

		URL url = httpBuilder.build().url();

		

		//Note: We would check that file properties contain fileuid & accountuid, but both are NonNull in obj def
		JsonObject props = file.toJson();

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			//Postgres (& SQL standard) requires single quotes around strings. What an absolute pain in the ass.
			if (key.equals("userattr"))
				builder.add(key, "'" + props.get(key) + "'");
			else
				builder.add(key, String.valueOf(props.get(key)).replace("\"", "'"));
		}
		RequestBody body = builder.build();





		Request request = new Request.Builder().url(url).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return true;
			//return new Gson().fromJson(responseData, SFile.class);
		}
	}
	

	//---------------------------------------------------------------------------------------------
	// Delete
	//---------------------------------------------------------------------------------------------

	public boolean delete(@NonNull UUID fileUID) throws IOException {
		//Log.i(TAG, String.format("\nDELETE FILE called with fileUID='"+fileUID+"'"));
		String url = Paths.get(baseServerUrl, "files", fileUID.toString()).toString();


		Request request = new Request.Builder().url(url).delete().build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return true;
			//return new Gson().fromJson(responseData, SFile.class);
		}
	}
}
