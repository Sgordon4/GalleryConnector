package com.example.galleryconnector.server;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.file.Paths;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Block {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SConnector";


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Get a presigned URL for reading a block
	public String getBlockUrl(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK GET URL called with blockHash='"+blockHash+"'"));
		String url = Paths.get(baseServerUrl, "blocks", "link", blockHash).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().string();
		}
	}


	//Get block data
	public byte[] getBlock(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK called with blockHash='"+blockHash+"'"));
		String url = Paths.get(baseServerUrl, "blocks", blockHash).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().bytes();
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------

	//Upload a block to the server
	public void uploadBlock(@NonNull String blockHash, @NonNull byte[] bytes) throws IOException {
		Log.i(TAG, String.format("\nUPLOAD BLOCK called with blockHash='"+blockHash+"'"));

		//Get the url we need to upload the block to
		String url = getBlockUploadUrl(blockHash);

		//Upload the block
		uploadBlockToUrl(bytes, url);

		//Create a new entry in the block table
		createBlockEntry(blockHash, bytes.length);

		Log.i(TAG, "Uploading block complete");
	}


	//Blocks are uploaded to a presigned url generated by the server
	private String getBlockUploadUrl(@NonNull String blockHash) throws IOException {
		Log.i(TAG, "GETTING BLOCK PUT URL...");
		String url = Paths.get(baseServerUrl, "blocks", "upload", blockHash).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().string();
		}
	}

	//Upload the block itself to the presigned url
	private void uploadBlockToUrl(@NonNull byte[] bytes, @NonNull String url) throws IOException {
		Log.i(TAG, "UPLOADING BLOCK...");

		Request upload = new Request.Builder()
				.url(url)
				.put(RequestBody.create(bytes, MediaType.parse("application/octet-stream")))
				.build();

		try (Response response = client.newCall(upload).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
		}
	}

	//TODO Have this triggered serverside by an IBM rule
	//Make an entry in the server's database table for this block
	private void createBlockEntry(@NonNull String blockHash, int blockSize) throws IOException {
		Log.i(TAG, "CREATING BLOCK ENTRY...");
		String url = Paths.get(baseServerUrl, "blocks", blockHash).toString();

		RequestBody body = new FormBody.Builder()
				.add("blocksize", String.valueOf(blockSize))
				.build();
		Request request = new Request.Builder()
				.url(url).put(body).build();


		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
		}
	}


	//---------------------------------------------------------------------------------------------
}
