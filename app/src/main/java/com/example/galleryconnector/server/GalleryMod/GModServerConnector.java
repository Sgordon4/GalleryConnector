package com.example.galleryconnector.server.GalleryMod;



import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;




/*
TODO
 - Create block (DONE)
 - Upload block (DONE)
 - Download block (DONE-ISH)
 - Create new file (DONE)
 - Commit fileset (DONE)
 - Get Journal > ID (DONE)

 - 'Delete' file

 - Create account
 - Update account

 */


//Look into NGINX reverse proxy so we don't have to do hacky shit to allow http for localhost
public class GModServerConnector {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SConnector";


	public GModServerConnector() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(3, TimeUnit.SECONDS)        //TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();
	}
	public static GModServerConnector getInstance() {
		return GModServerConnector.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GModServerConnector INSTANCE = new GModServerConnector();
	}

	public static class LogInterceptor implements Interceptor {
		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			Log.i(TAG, "");
			Log.i(TAG, String.format("OKHTTP: %s --> %s", request.method(), request.url()));
			if(request.body() != null)
				Log.d(TAG, String.format("OKHTTP: %s", request.body().toString()));

			long t1 = System.nanoTime();
			Response response = chain.proceed(request);
			long t2 = System.nanoTime();

			Log.i(TAG, String.format("OKHTTP: Received response %s for %s in %.1fms",
					response.code(), response.request().url(), (t2 - t1) / 1e6d));
			//Log.v(TAG, String.format("%s", response.headers()));
			Log.v(TAG, String.format("%s", response.peekBody(400).string()));

			return response;
		}
	}



	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	@Nullable
	public JsonObject getAccount(@NonNull UUID accountID) {
		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/accounts/"+accountID);
		Request request = builder.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("Unexpected code "+response.code()+": "+response.message());
			}

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	//TODO
	public Boolean fileExists(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub");
	}

	public JsonObject getFileProps(@NonNull UUID fileUID) {
		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/files/"+fileUID);
		Request request = builder.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("Unexpected code "+response.code()+": "+response.message());
			}

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}


	public JsonObject createFile(@NonNull UUID ownerUID, boolean isDir, boolean isLink) {
		RequestBody body = new FormBody.Builder()
				.add("owneruid", String.valueOf(ownerUID))
				.add("isdir", String.valueOf(isDir))
				.add("islink", String.valueOf(isLink))
				.build();

		Request request = new Request.Builder()
				.url(baseServerUrl +"/files/")
				.post(body)
				.build();


		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);

		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}


	public List<String> commitFileBlockset(@NonNull UUID fileUID, List<String> fileblocks) throws IOException {
		RequestBody body = new FormBody.Builder()
				.add("fileblocks", new Gson().toJson(fileblocks))
				.build();

		Request request = new Request.Builder()
				.url(baseServerUrl +"/files/commit/"+fileUID)
				.put(body)
				.build();


		try (Response response = client.newCall(request).execute()) {
			//If code == 400, there are missing blocks
			if(response.code() == 400) {
				String respBody = response.body().string();

				//Get which blocks are missing
				JsonObject obj = new Gson().fromJson(respBody, JsonObject.class);
				List<String> entries = new Gson().fromJson(obj.get("missingblocks"),
						new TypeToken< List<String> >(){}.getType());

				Log.i(TAG, "commitFileBlockset: Missing blocks: "+entries);
				return entries;
			}

			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}

			//No blocks are missing, all good
			return new ArrayList<>();
		} catch (Exception e){
			e.printStackTrace();
			throw e;
		}
	}





	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	public byte[] getBlock(@NonNull String blockHash) throws IOException {
		System.out.println("\nGET BLOCK called with blockHash='"+blockHash+"'");

		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/blocks/"+blockHash);
		Request request = builder.build();


		System.out.println("Fetching block...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}
			System.out.println("Successfully fetched block.");
			System.out.println("Blocksize: "+response.body().contentLength());

			return response.body().bytes();
		}catch (Exception e){
			e.printStackTrace();
			throw e;
		}
	}


	//---------------------------------------------------------------------------------------------

	//TODO Compress and encrypt before upload
	public void uploadBlock(@NonNull String blockHash, @NonNull byte[] bytes) throws IOException {
		System.out.println("\nUPLOAD BLOCK MAIN called with blockHash='"+blockHash+"'");

		try {

			//Get the url we need to upload the block to
			String url = getBlockUploadUrl(blockHash);

			//Upload the block
			uploadBlockToUrl(bytes, url);

			//Create a new entry in the block table
			createBlock(blockHash, bytes.length);

		} catch (IOException e) {
			System.out.println("Uploading to block failed!");
			e.printStackTrace();
			throw e;
		}
	}


	//---------------------------------------------------------------------------------------------

	public boolean blockExists(@NonNull String blockHash) throws IOException {
		System.out.println("\nBLOCK EXISTS called with blockHash='"+blockHash+"'");

		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/blocks/exists/"+blockHash);
		Request request = builder.build();

		System.out.println("Fetching block exists...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}

			String responseData = response.body().string();
			System.out.println("Response: \n"+responseData);

			return true;
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
	}


	//---------------------------------------------------------------------------------------------

	@Nullable
	private String getBlockUploadUrl(@NonNull String blockHash) throws IOException {
		System.out.println("\nGET BLOCK URL called with blockHash='"+blockHash+"'");

		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/blocks/upload/"+blockHash);
		Request request = builder.build();

		System.out.println("Fetching block upload url...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			return response.body().string();
		}
	}


	//---------------------------------------------------------------------------------------------

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

	private void createBlock(@NonNull String blockHash, int blockSize) throws IOException {
		System.out.println("\nCREATE BLOCK called with blockHash='"+blockHash+"'");
		System.out.println("BlockSize: "+blockSize);

		RequestBody body = new FormBody.Builder()
				.add("blocksize", String.valueOf(blockSize))
				.build();

		Request request = new Request.Builder()
				.url(baseServerUrl +"/blocks/"+blockHash)
				.put(body)
				.build();


		System.out.println("Creating new block...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}
			System.out.println("Block created!");
		}catch (Exception e){
			e.printStackTrace();
			throw e;
		}
	}



	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	public List<JsonObject> getJournalEntriesAfter(int journalID) throws IOException {
		System.out.println("\nGET JOURNAL called with journalID='"+journalID+"'");

		Request.Builder builder = new Request.Builder();
		builder.url(baseServerUrl +"/journal/"+journalID);
		Request request = builder.build();

		System.out.println("Fetching journal entries...");
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(response);
				throw new IOException("Unexpected code " + response.code());
			}

			String responseData = response.body().string();
			List<JsonObject> entries = new Gson().fromJson(responseData,
					new TypeToken< List<JsonObject> >(){}.getType());
			System.out.println(entries.size()+" entries received.");

			return entries;
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
	}














}
