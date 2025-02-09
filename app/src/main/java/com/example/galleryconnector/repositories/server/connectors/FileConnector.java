package com.example.galleryconnector.repositories.server.connectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
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

			"filesize",
			"filehash",

			"userattr",
			"attrhash",

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

	//Instead of making an endpoint for this one, we're just checking if getProps() throws a FileNotFound exception
	public Boolean exists(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub");
	}

	@NonNull
	public SFile getProps(@NonNull UUID fileUID) throws FileNotFoundException, IOException {
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

			return new Gson().fromJson(responseData.trim(), SFile.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create or update a file entry in the database
	public SFile upsert(@NonNull SFile file, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws IllegalStateException, IOException {
		//Log.i(TAG, "\nUPSERT FILE called");
		String base = Paths.get(baseServerUrl, "files").toString();

		//Empty user attributes "{}" are hashed to "BF21A9E8FBC5A3846FB05B4FA0859E0917B2202F". This is swapped in for convenience.
		if(prevAttrHash == null) prevAttrHash = "BF21A9E8FBC5A3846FB05B4FA0859E0917B2202F";

		//Alongside the usual url, send fileHash and attrHash as query params if applicable
		HttpUrl.Builder httpBuilder = HttpUrl.parse(base).newBuilder();
		if(prevFileHash != null) httpBuilder.addQueryParameter("prevfilehash", prevFileHash);
		httpBuilder.addQueryParameter("prevattrhash", prevAttrHash);
		URL url = httpBuilder.build().url();


		//Note: No need to check that file properties contain fileuid & accountuid, both are NonNull in obj def
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
			if(response.code() == 412)
				throw new IllegalStateException("PrevHashes do not match with latest properties!");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, SFile.class);
		}
	}
	

	//---------------------------------------------------------------------------------------------
	// Delete
	//---------------------------------------------------------------------------------------------

	public void delete(@NonNull UUID fileUID) throws FileNotFoundException, IOException {
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

			//String responseData = response.body().string();
			//return new Gson().fromJson(responseData, SFile.class);
		}
	}
}
