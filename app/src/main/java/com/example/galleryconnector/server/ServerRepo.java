package com.example.galleryconnector.server;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.server.connectors.AccountConnector;
import com.example.galleryconnector.server.connectors.BlockConnector;
import com.example.galleryconnector.server.connectors.FileConnector;
import com.example.galleryconnector.server.connectors.JournalConnector;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ServerRepo {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SRepo";

	public final AccountConnector accountConn;
	public final FileConnector fileConn;
	public final BlockConnector blockConn;
	public final JournalConnector journalConn;


	public ServerRepo() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(3, TimeUnit.SECONDS)	//TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();

		accountConn = new AccountConnector(baseServerUrl, client);
		fileConn = new FileConnector(baseServerUrl, client);
		blockConn = new BlockConnector(baseServerUrl, client);
		journalConn = new JournalConnector(baseServerUrl, client);
	}

	public static ServerRepo getInstance() {
		return ServerRepo.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ServerRepo INSTANCE = new ServerRepo();
	}


	//---------------------------------------------------------------------------------------------


	public JsonObject uploadFile(@NonNull JsonObject fileProps, @NonNull Uri source,
								 @NonNull Context context) throws IOException {
		Log.i(TAG, String.format("UPLOAD FILE called with fileUID='%s'", fileProps.get("fileuid").getAsString()));

		//Upload the blockset for the file. This does nothing if all blocks already exist.
		Map<String, String> fileHashAndSize = blockConn.uploadBlockset(source, context);

		//Update the file properties with the hash and size
		fileProps.addProperty("blockset", fileHashAndSize.get("blockset"));
		fileProps.addProperty("filehash", fileHashAndSize.get("filehash"));
		fileProps.addProperty("filesize", fileHashAndSize.get("filesize"));


		//TODO Maybe cache the file? Probably best to be done in GalleryRepo alongside a call to this function


		//Now that the blockset is uploaded, create/update the file metadata
		return fileConn.upsert(fileProps);
	}


	//TODO This should go in galleryconn, not here. Need to cache the file
	public void downloadFullFile(@NonNull UUID fileUID, @NonNull Uri dest) {

	}







	//---------------------------------------------------------------------------------------------

	//TODO Figure out how to log timeouts
	public static class LogInterceptor implements Interceptor {
		@NonNull
		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			//Log.i(TAG, "");
			Log.i(TAG, String.format("	OKHTTP: %s --> %s", request.method(), request.url()));
			//if(request.body() != null)	//Need another method to print body, this no worky
				//Log.d(TAG, String.format("OKHTTP: Sending with body - %s", request.body()));

			long t1 = System.nanoTime();
			Response response = chain.proceed(request);
			long t2 = System.nanoTime();

			Log.i(TAG, String.format("	OKHTTP: Received response %s for %s in %.1fms",
					response.code(), response.request().url(), (t2 - t1) / 1e6d));

			//Log.v(TAG, String.format("%s", response.headers()));
			if(response.body() != null)
				Log.v(TAG, "	OKHTTP: Returned with body length of "+response.body().contentLength());
			else
				Log.v(TAG, "	OKHTTP: Returned with null body");

			return response;
		}
	}
}
