package com.example.galleryconnector.server.subcomponents;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
	private static final String TAG = "GCon.Block";

	static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB



	public BlockConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Get a presigned URL for reading a block
	public String getUrl(@NonNull String blockHash) throws IOException {
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


	//Get actual block data
	public byte[] getData(@NonNull String blockHash) throws IOException {
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


	public JsonArray getProps(@NonNull List<String> blocks) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK PROPS called with blocks='%s'", blocks));

		//Alongside the usual url, compile all passed blocks into query parameters
		String base = Paths.get(baseServerUrl, "blocks", "props").toString();
		System.out.println("A");
		HttpUrl.Builder httpBuilder = HttpUrl.parse(base).newBuilder();
		System.out.println("B");
		for(String block : blocks) {
			System.out.println(block);
			httpBuilder.addQueryParameter("blockhash", block);
		}
		System.out.println("C");

		URL url = httpBuilder.build().url();//Why it breaking right here?
		//Wait no now it's just breaking somewhere else. What in the hell is going on?


		System.out.println("Here");
		System.out.println("Here");
		System.out.println("Here");
		System.out.println("Here");

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			System.out.println("Data");
			System.out.println("Data");
			System.out.println("Data");
			System.out.println(responseData);
			return new Gson().fromJson(responseData, JsonArray.class);
		}
	}


	/*
	//TODO No endpoint for this one yet
	public JsonArray exists(@NonNull List<String> blockHashes) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK EXISTS called with blockHashes='"+blockHashes+"'"));
		String url = Paths.get(baseServerUrl, "blocks", "exist").toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class).getAsJsonArray();
		}
	}
	 */


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------

	//Upload a block to the server
	public void uploadData(@NonNull String blockHash, @NonNull byte[] bytes) throws IOException {
		Log.i(TAG, String.format("\nUPLOAD BLOCK called with blockHash='"+blockHash+"'"));

		//Get the url we need to upload the block to
		String url = getUploadUrl(blockHash);

		//Upload the block
		uploadToUrl(bytes, url);

		//Create a new entry in the block table
		createEntry(blockHash, bytes.length);

		Log.i(TAG, "Uploading block complete");
	}


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


	//Returns blockset, filehash, and filesize
	public Map<String, String> uploadBlockset(@NonNull Uri source, @NonNull Context context) throws IOException {
		Log.i(TAG, String.format("UPLOAD BLOCKSET called with uri='%s'", source));
		ContentResolver contentResolver = context.getContentResolver();

		//We need to know what blocks in the blocklist the server is missing.
		//To do that, we need the blocklist. Get the blocklist.
		List<String> fileHashes = new ArrayList<>();
		//Find the filesize and SHA-256 filehash while we do so.
		int filesize = 0;
		String filehash;
		try (InputStream is = contentResolver.openInputStream(source);
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			//Read the next block
			byte[] block = new byte[CHUNK_SIZE];
			int read;
			while((read = dis.read(block)) != -1) {
				//Trim block if needed (tail of the file, not enough bytes to fill a full block)
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;
				filesize += block.length;

				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = BlockConnector.bytesToHex(hash);

				//Add to the hash list
				fileHashes.add(hashString);
			}

			filehash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		Log.d(TAG, "FileHashes: "+fileHashes);


		//Now try to upload/commit blocks
		List<String> missingBlocks;
		do {
			//Get the list of missing blocks
			missingBlocks = getMissingBlocks(fileHashes);

			for(String missingBlockHash : missingBlocks) {

				//Go to the correct block in the file
				int index = fileHashes.indexOf(missingBlockHash);
				int blockStart = index * CHUNK_SIZE;

				Log.d(TAG, String.format("BSUpload: Reading block at %s = '%s'", blockStart, missingBlockHash));
				try (InputStream is = contentResolver.openInputStream(source)) {
					//Read the missing block
					is.skip(blockStart);
					byte[] block = new byte[CHUNK_SIZE];
					int read = is.read(block);

					//Trim block if needed
					if (read != CHUNK_SIZE) {
						byte[] smallerData = new byte[read];
						System.arraycopy(block, 0, smallerData, 0, read);
						block = smallerData;
					}

					//Upload it
					uploadData(missingBlockHash, block);
				}
			}
		} while (!missingBlocks.isEmpty());
		Log.d(TAG, "Successful blockset upload!");


		Map<String, String> fileInfo = new HashMap<>();
		fileInfo.put("blockset", new Gson().toJson(missingBlocks));
		fileInfo.put("filehash", filehash);
		fileInfo.put("filesize", String.valueOf(filesize));
		return fileInfo;
	}


	private List<String> getMissingBlocks(List<String> blocks) throws IOException {
		JsonArray existingBlocks = getProps(blocks);

		for(JsonElement block : existingBlocks) {
			blocks.remove(block.getAsString());
		}

		return blocks;
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
