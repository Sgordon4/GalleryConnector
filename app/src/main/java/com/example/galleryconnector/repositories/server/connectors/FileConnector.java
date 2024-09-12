package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
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

			"fileblocks",
			"filesize",
			"filehash",

			"isdeleted",
			"changetime",
			"modifytime",
			"accesstime",
			"createtime"
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

	public JsonObject getProps(@NonNull UUID fileUID) throws IOException {
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
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create or update a file entry in the database
	public JsonObject upsert(@NonNull JsonObject props) throws IOException {
		Log.i(TAG, "\nUPSERT FILE called");
		String url = Paths.get(baseServerUrl, "files", "upsert").toString();

		String[] reqInsert = {"fileuid", "accountuid"};
		if(!props.has(reqInsert[0]) || !props.has(reqInsert[1]))
			throw new IllegalArgumentException("File upsert request must contain fileuid & accountuid!");


		//Compile all passed properties into a form body
		FormBody.Builder builder = new FormBody.Builder();
		System.out.println("Keyset: "+props.keySet());
		for(String key : props.keySet()) {
			System.out.println("Key: "+key+" Value: "+props.get(key));
			System.out.println("ToString: "+props.get(key).toString());
			builder.add(key, props.get(key).toString());
		}
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(url).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	/*
	//Create a new file entry in the database
	public JsonObject createEntry(@NonNull JsonObject props) throws IOException {
		Log.i(TAG, "\nCREATE FILE called");
		String url = Paths.get(baseServerUrl, "files", "insert").toString();

		String[] reqInsert = {"fileuid", "accountuid"};
		if(!props.has(reqInsert[0]) || !props.has(reqInsert[1]))
			throw new IllegalArgumentException("File creation request must contain fileuid & accountuid!");


		//Compile all passed properties into a form body
		FormBody.Builder builder = new FormBody.Builder();
		for(String prop : props.keySet()) {
			builder.add(prop, props.get(prop).getAsString());
		}
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(url).post(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	public JsonObject updateEntry(@NonNull JsonObject props) throws IOException {
		if(!props.has("fileuid"))
			throw new IllegalArgumentException("File update request must contain fileuid!");

		UUID fileUID = UUID.fromString(props.get("fileuid").getAsString());
		Log.i(TAG, "\nUPDATE FILE called with fileUID='"+fileUID+"'");
		String url = Paths.get(baseServerUrl, "files", "update", fileUID.toString()).toString();

		//Note: This isn't checking that any usable props are sent, maybe we should but server can do that
		if(!(props.keySet().size() > 1))
			throw new IllegalArgumentException("File update request must contain " +
					"at least one property other than fileuid!");


		//Compile all passed properties into a form body
		FormBody.Builder builder = new FormBody.Builder();
		for(String prop : props.asMap().keySet()) {
			builder.add(prop, props.get(prop).getAsString());
		}
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(url).post(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}
	 */


	//---------------------------------------------------------------------------------------------
	// Delete
	//---------------------------------------------------------------------------------------------

	public JsonObject delete(@NonNull UUID fileUID) throws IOException {
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
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}
}
