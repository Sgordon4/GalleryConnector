package com.example.galleryconnector.repositories.server.connectors;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.server.servertypes.SBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BlockConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "Gal.SRepo.Block";

	//public static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB
	public static final int CHUNK_SIZE = 1024 * 1024 * 1;  //1MB FOR TESTING



	public BlockConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------


	public SBlock getProps(@NonNull String block) throws IOException {
		List<SBlock> arr = getProps(Collections.singletonList(block));

		if(arr.isEmpty()) return null;
		return arr.get(0);
	}

	public List<SBlock> getProps(@NonNull List<String> blocks) throws IOException {
		//Log.i(TAG, String.format("\nGET BLOCK PROPS called with blocks='%s'", blocks));

		//Alongside the usual url, compile all passed blocks into query parameters
		String base = Paths.get(baseServerUrl, "blocks", "props").toString();
		HttpUrl.Builder httpBuilder = HttpUrl.parse(base).newBuilder();

		for(String block : blocks)
			httpBuilder.addQueryParameter("blockhash", block);
		URL url = httpBuilder.build().url();


		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();

			Gson gson = new GsonBuilder()
					.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
							Instant.parse(json.getAsString()))
					.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (instant, type, jsonSerializationContext) ->
							new JsonPrimitive(instant.toString()
							))
					.create();

			return gson.fromJson(responseData, new TypeToken< List<SBlock> >(){}.getType());
			//return new Gson().fromJson(responseData, new TypeToken< List<SBlock> >(){}.getType());
		}
	}


	//Get a presigned URL for reading block data
	public String getUrl(@NonNull String blockHash) throws DataNotFoundException, IOException {
		//Log.i(TAG, String.format("\nGET BLOCK GET URL called with blockHash='"+blockHash+"'"));
		String url = Paths.get(baseServerUrl, "blocks", "link", blockHash).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new DataNotFoundException(blockHash);
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().string();
		}
	}

	public byte[] readBlock(@NonNull String blockHash) throws DataNotFoundException, IOException {
		//Log.i(TAG, String.format("\nGET BLOCK called with blockHash='"+blockHash+"'"));
		String url = Paths.get(baseServerUrl, "blocks", blockHash).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new DataNotFoundException(blockHash);
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


	//Blocks are uploaded to a presigned url generated by the server
	private String getUploadUrl(@NonNull String blockHash) throws IOException {
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

	//Upload a block to the server
	public String uploadData(@NonNull byte[] bytes) throws IOException {
		//Hash the block
		String blockHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			blockHash = BlockConnector.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		//Log.i(TAG, String.format("\nUPLOAD BLOCK called with blockHash='"+blockHash+"'"));


		//TODO Check if block exists in the server first


		//Get the url we need to upload the block to
		String url = getUploadUrl(blockHash);

		//Upload the block
		uploadToUrl(bytes, url);

		//Create a new entry in the block table
		createEntry(blockHash, bytes.length);

		Log.i(TAG, "Uploading block complete");
		return blockHash;
	}



	//Upload the block itself to the presigned url
	private void uploadToUrl(@NonNull byte[] bytes, @NonNull String url) throws IOException {
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

	//TODO Have this triggered serverside by an IBM rule, not from ServerRepo
	//Make an entry in the server's database table for this block
	private void createEntry(@NonNull String blockHash, int blockSize) throws IOException {
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

	//https://stackoverflow.com/a/9855338
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	public static String bytesToHex(@NonNull byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}
}
