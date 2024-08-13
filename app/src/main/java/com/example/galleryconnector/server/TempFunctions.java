package com.example.galleryconnector.server;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


//Note to me: All of this was grabbed from GalleryModularized's ServerConnector (with some flow changes)

public class TempFunctions {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SConnector";

	public TempFunctions() {
		client = new OkHttpClient().newBuilder()
				//.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(3, TimeUnit.SECONDS)        //TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();
	}

	public static class LogInterceptor implements Interceptor {
		@NonNull
		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			//Log.i(TAG, "");
			Log.i(TAG, String.format("OKHTTP: %s --> %s", request.method(), request.url()));
			if(request.body() != null)
				Log.d(TAG, String.format("OKHTTP: Sending with body - %s", request.body()));

			long t1 = System.nanoTime();
			Response response = chain.proceed(request);
			long t2 = System.nanoTime();

			Log.i(TAG, String.format("OKHTTP: Received response %s for %s in %.1fms",
					response.code(), response.request().url(), (t2 - t1) / 1e6d));

			//Log.v(TAG, String.format("%s", response.headers()));
			if(response.body() != null)
				Log.v(TAG, String.format("OKHTTP: Returned with body length of %s", response.body()));
			else
				Log.v(TAG, "OKHTTP: Returned with null body");

			return response;
		}
	}


	//---------------------------------------------------------------------------------------------



	public void tempGetBlocks(List<String> hashes) {
		ExecutorService executorService = Executors.newFixedThreadPool(4);
		Runnable getBlockUris = () -> {
			Block blockAPI = new Block();
			List<String> uris = new ArrayList<>();
			for(String hash : hashes) {
				try {
					byte[] blockThing = blockAPI.getBlock(hash);
					String blck = new String(blockThing);
					uris.add(blck);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			System.out.println("Blocks received: ");
			for(String uri : uris) {
				System.out.println(uri);
			}
		};
		executorService.execute(getBlockUris);
	}


	public void tempUpload(Uri fileUri, ContentResolver contentResolver) throws IOException {
		//Uri fileUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ocean_with_audio);

		ExecutorService executorService = Executors.newFixedThreadPool(4);

		Runnable upload = () -> {
			TempFunctions blockAPI = new TempFunctions();
			try {
				blockAPI.uploadFile(fileUri, contentResolver);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};

		executorService.execute(upload);
	}









	//---------------------------------------------------------------------------------------------


	@Nullable
	private String getBlockUploadUrl(@NonNull String blockHash) throws IOException {
		System.out.println("\nGET BLOCK URL called with blockHash='"+blockHash+"'");

		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/blocks/upload/"+blockHash);
		Request request = builder.build();

		System.out.println("Request URL: "+baseServerUrl +"/blocks/upload/"+blockHash);

		System.out.println("Fetching block upload url...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println("UNSUCCESSFUL");
				throw new IOException("Unexpected code " + response.code());
			}

			return response.body().string();
		}
	}

	private void uploadBlockToUrl(@NonNull byte[] bytes, @NonNull String url) throws IOException {
		Request upload = new Request.Builder()
				.url(url)
				.put(RequestBody.create(MediaType.parse("application/octet-stream"), bytes))
				.build();

		System.out.println("Writing to block upload url...");
		try (Response response = client.newCall(upload).execute()) {
			System.out.println(response);

			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
		}
	}




	//---------------------------------------------------------------------------------------------


	static final int CHUNK_SIZE = 1024 * 1024 * 4;  //4MB

	public void uploadFile(@NonNull Uri source, ContentResolver contentResolver) throws IOException {
		System.out.println("Attempting to upload");

		//We need to know what blocks in the blocklist the server is missing.
		//To do that, we need to attempt to commit the file blocklist to the server.
		//To do that, we need the blocklist. Get the blocklist:
		List<String> fileHashes = new ArrayList<>();
		try (InputStream is = contentResolver.openInputStream(source)) {
			//Read the next block
			byte[] block = new byte[CHUNK_SIZE];
			int read;
			while((read = is.read(block)) != -1) {
				//Trim block if needed
				if (read != CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;
				}

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;

				//Hash the block
				byte[] hash = MessageDigest.getInstance("SHA-256").digest(block);
				String hashString = bytesToHex(hash);

				//Add to the hash list
				fileHashes.add(hashString);
			}
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		System.out.println("FileHashes: \n"+fileHashes);



		//Upload blocks
		for(String hash : fileHashes) {
			//Go to the correct block
			int index = fileHashes.indexOf(hash);
			int blockStart = index * CHUNK_SIZE;

			System.out.println("Reading block at "+blockStart+" = '"+hash+"'");
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

				//Throw it to the wind
				new Block().uploadBlock(hash, block);
			} catch (IOException e) {
				System.out.println("Block upload failed with hash "+hash);
				e.printStackTrace();
			}
		}

		System.out.println("Successful upload!");
	}


	//-------------------------------------------------


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
