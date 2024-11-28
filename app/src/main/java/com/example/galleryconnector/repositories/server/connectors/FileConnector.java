package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import okhttp3.FormBody;
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
		Log.i(TAG, String.format("\nGET FILE called with fileUID='"+fileUID+"'"));
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

			System.out.println(responseData);
			JsonObject responseJson = JsonParser.parseString(responseData).getAsJsonObject();
			System.out.println("RESPONSE");
			System.out.println(responseJson);
			//Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();

			JsonDeserializer<Date> dateJsonDeserializer =
					(json, typeOfT, context) -> json == null ? null : new Date(json.getAsLong());
			Gson gson = new GsonBuilder().registerTypeAdapter(Date.class,dateJsonDeserializer).create();

			//return gson.fromJson(responseData.trim(), SFile.class);
			return new Gson().fromJson(responseData.trim(), SFile.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create or update a file entry in the database
	public boolean upsert(@NonNull SFile file) throws IOException {
		Log.i(TAG, "\nUPSERT FILE called");
		String url = Paths.get(baseServerUrl, "files", "upsert").toString();

		file.userattr.addProperty("potato", "potato");

		System.out.println("FILE PROPS");
		System.out.println(file.toJson());

		//Note: We would check that file properties contain fileuid & accountuid, but both are NonNull in obj def
		JsonObject props = file.toJson();
		Log.v(TAG, "Keyset: "+props.keySet());

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			System.out.println("Key: "+key+" Value: "+props.get(key));

			//Postgres (& SQL standard) requires single quotes around strings. What an absolute pain in the ass.
			switch (key) {
				case "userattr":
					builder.add(key, "'"+props.get(key)+"'");
					break;
				case "changetime":
				case "modifytime":
				case "accesstime":
				case "createtime":
					System.out.println("Date Val: "+props.get(key));
					builder.add(key, "'"+ Instant.parse(props.get(key).toString())+"'");
					break;
				default:
					builder.add(key, String.valueOf(props.get(key)).replace("\"", "'"));
					break;
			}

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
		Log.i(TAG, String.format("\nDELETE FILE called with fileUID='"+fileUID+"'"));
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
