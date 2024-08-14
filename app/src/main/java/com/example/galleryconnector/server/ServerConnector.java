package com.example.galleryconnector.server;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.server.subcomponents.AccountConnector;
import com.example.galleryconnector.server.subcomponents.BlockConnector;
import com.example.galleryconnector.server.subcomponents.FileConnector;
import com.example.galleryconnector.server.subcomponents.JournalConnector;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ServerConnector {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SConnector";

	public final AccountConnector account;
	public final FileConnector file;
	public final BlockConnector block;
	public final JournalConnector journal;


	public ServerConnector() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(3, TimeUnit.SECONDS)        //TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();

		account = new AccountConnector(baseServerUrl, client);
		file = new FileConnector(baseServerUrl, client);
		block = new BlockConnector(baseServerUrl, client);
		journal = new JournalConnector(baseServerUrl, client);
	}

	public static ServerConnector getInstance() {
		return ServerConnector.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ServerConnector INSTANCE = new ServerConnector();
	}












	//---------------------------------------------------------------------------------------------

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
}
