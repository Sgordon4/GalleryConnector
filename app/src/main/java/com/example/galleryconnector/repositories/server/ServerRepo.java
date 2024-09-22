package com.example.galleryconnector.repositories.server;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.server.connectors.AccountConnector;
import com.example.galleryconnector.repositories.server.connectors.FileConnector;
import com.example.galleryconnector.repositories.server.connectors.JournalConnector;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


//TODO Eventually change most/all of the serverRepo.blockConn or fileConn or whatever to just the SRepo method

public class ServerRepo {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	//private static final String baseServerUrl = "http://localhost:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SRepo";

	public final AccountConnector accountConn;
	public final FileConnector fileConn;
	public final BlockConnector blockConn;
	public final JournalConnector journalConn;

	private final ServerFileObservers observers;


	public ServerRepo() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(5, TimeUnit.SECONDS)	//TODO Temporary timeout, prob increase later
				.followSslRedirects(true)
				.build();

		accountConn = new AccountConnector(baseServerUrl, client);
		fileConn = new FileConnector(baseServerUrl, client);
		blockConn = new BlockConnector(baseServerUrl, client);
		journalConn = new JournalConnector(baseServerUrl, client);

		observers = new ServerFileObservers();
	}

	public static ServerRepo getInstance() {
		return ServerRepo.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ServerRepo INSTANCE = new ServerRepo();
	}

	//---------------------------------------------------------------------------------------------

	public void addObserver(ServerFileObservers.SFileObservable observer) {
		observers.addObserver(observer);
	}
	public void removeObserver(ServerFileObservers.SFileObservable observer) {
		observers.removeObserver(observer);
	}

	public void startListening(int journalID, UUID accountUID) {
		observers.startListening(journalID, accountUID);
	}
	public void stopListening() {
		observers.stopListening();
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public JsonObject getFileProps(@NonNull UUID fileUID) throws IOException {
		Log.i(TAG, String.format("GET FILE called with fileUID='%s'", fileUID));

		return fileConn.getProps(fileUID);
	}


	public void putFileProps(@NonNull JsonObject fileProps) throws IOException {
		Log.i(TAG, String.format("PUT FILE called with fileUID='%s'", fileProps.get("fileuid")));

		//Grab the blockset from the file properties
		Type listType = new TypeToken<List<String>>() {}.getType();
		List<String> blockset = new Gson().fromJson(fileProps.get("fileblocks"), listType);

		//Check if the blocks repo is missing any blocks from the blockset
		List<String> missingBlocks = getMissingBlocks(blockset);


		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Now that we've confirmed all blocks exist, create/update the file metadata
		fileConn.upsert(fileProps);

		//TODO Maybe cache the file?
	}
	public List<String> getMissingBlocks(List<String> blockset) {
		//Check if the blocks repo is missing any blocks from the blockset
		return blockset.stream()
				.filter(block -> {
					try {
						return blockConn.getProps(block) != null;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}).collect(Collectors.toList());
	}


	//TODO This should go in galleryconn, not here. Need to cache the file
	public void downloadFullFile(@NonNull UUID fileUID, @NonNull Uri dest) {

	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	public JsonObject getBlockProps(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("GET BLOCK PROPS called with blockHash='%s'", blockHash));

		return blockConn.getProps(blockHash);
	}

	public byte[] getBlockData(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("GET BLOCK DATA called with blockHash='%s'", blockHash));

		return blockConn.getData(blockHash);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------


	public List<JsonObject> getJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("GET JOURNAL ENTRIES called with journalID='%s'", journalID));

		return journalConn.getJournalEntriesAfter(journalID);
	}

	public List<JsonObject> getJournalEntriesForFile(UUID fileUID) throws IOException {
		Log.i(TAG, String.format("GET JOURNAL ENTRIES FOR FILE called with fileUID='%s'", fileUID));

		return journalConn.getJournalEntriesForFile(fileUID);
	}

	public List<JsonObject> longpollJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("LONGPOLL JOURNAL ENTRIES called with journalID='%s'", journalID));

		return journalConn.longpollJournalEntriesAfter(journalID);
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
