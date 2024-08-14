package com.example.galleryconnector.server;

import android.util.Log;

import androidx.annotation.NonNull;

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

	Account account;
	File file;
	Block block;
	Journal journal;


	public ServerConnector() {
		client = new OkHttpClient().newBuilder()
				//.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(3, TimeUnit.SECONDS)        //TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();

		account = new Account(baseServerUrl, client);
		file = new File(baseServerUrl, client);
		block = new Block(baseServerUrl, client);
		journal = new Journal(baseServerUrl, client);
	}

	public static ServerConnector getInstance() {
		return ServerConnector.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ServerConnector INSTANCE = new ServerConnector();
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
}
