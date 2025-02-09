package com.example.galleryconnector.repositories.server;

import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.combined.combinedtypes.ContentsNotFoundException;
import com.example.galleryconnector.repositories.server.connectors.AccountConnector;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;
import com.example.galleryconnector.repositories.server.connectors.FileConnector;
import com.example.galleryconnector.repositories.server.connectors.JournalConnector;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SContent;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class ServerRepo {
	private static final String baseServerUrl = "http://10.0.2.2:3306";
	//private static final String baseServerUrl = "http://localhost:3306";
	OkHttpClient client;
	private static final String TAG = "Gal.SRepo";

	public final AccountConnector accountConn;
	public final FileConnector fileConn;
	public final ContentConnector contentConn;
	public final JournalConnector journalConn;

	private final ServerFileObservers observers;


	public ServerRepo() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(2, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)		//Long timeout for longPolling
				.writeTimeout(5, TimeUnit.SECONDS)
				.followSslRedirects(true)
				.build();

		accountConn = new AccountConnector(baseServerUrl, client);
		fileConn = new FileConnector(baseServerUrl, client);
		contentConn = new ContentConnector(baseServerUrl, client);
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
	// Account
	//---------------------------------------------------------------------------------------------


	public SAccount getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException, ConnectException {
		Log.i(TAG, String.format("GET SERVER ACCOUNT PROPS called with accountUID='%s'", accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		JsonObject accountProps;
		try {
			accountProps = accountConn.getProps(accountUID);
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if(accountProps == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return new Gson().fromJson(accountProps, SAccount.class);
	}

	public void putAccountProps(@NonNull SAccount accountProps) throws ConnectException {
		Log.i(TAG, String.format("PUT SERVER ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			accountConn.updateEntry(accountProps.toJson());
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	@NonNull
	public SFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		Log.v(TAG, String.format("GET SERVER FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return fileConn.getProps(fileUID);
		} catch (FileNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}


	public SFile putFileProps(@NonNull SFile fileProps, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws ContentsNotFoundException, IllegalStateException, ConnectException {
		Log.i(TAG, String.format("PUT SERVER FILE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		//Check if the server is missing the file contents. If so, we can't commit the file changes
		if(fileProps.filehash != null) {
			try {
				contentConn.getProps(fileProps.filehash);
			} catch (ContentsNotFoundException e) {
				throw new ContentsNotFoundException("Cannot put props, system is missing file contents!");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}


		//Now that we've confirmed the contents exist, create/update the file metadata
		try {
			return fileConn.upsert(fileProps, prevFileHash, prevAttrHash);
		} catch (IllegalStateException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void deleteFileProps(@NonNull UUID fileUID) throws ConnectException {
		Log.i(TAG, String.format("DELETE SERVER FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			fileConn.delete(fileUID);
		} catch (FileNotFoundException e) {
			//Do nothing
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	/*
	public SContent getBlockProps(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		Log.v(TAG, String.format("GET SERVER BLOCK PROPS called with blockHash='%s'", blockHash));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		SContent block;
		try {
			block = blockConn.getProps(blockHash);
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if(block == null) throw new FileNotFoundException("Block not found! Hash: '"+blockHash);
		return block;
	}
	public boolean getBlockPropsExist(@NonNull String blockHash) throws ConnectException {
		try {
			getBlockProps(blockHash);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (SocketException e) {
			throw new ConnectException();
		}
	}
	 */


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------


	public SContent getContentProps(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		try {
			return contentConn.getProps(name);
		} catch (ContentsNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public Uri getContentDownloadUri(@NonNull String name) throws ContentsNotFoundException, ConnectException {
		Log.v(TAG, String.format("\nGET SERVER CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			//Throws a ContentsNotFound exception if the content properties don't exist
			getContentProps(name);

			//Now that we know the properties exist, return the content uri
			return Uri.parse(contentConn.getDownloadUrl(name));
		} catch (ContentsNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	//No delete method, we want that to be done by automatic jobs on the server, not here



	//Helper method
	//Source file must be on-disk
	//Returns the filesize of the provided source
	//WARNING: DOES NOT UPDATE FILE PROPERTIES
	public SContent uploadData(@NonNull String name, @NonNull File source) throws FileNotFoundException, ConnectException {
		Log.i(TAG, "\nPUT SERVER CONTENTS called with source='"+source.getPath()+"'");

		if (!source.exists()) throw new FileNotFoundException("Source file not found! Path: '"+source.getPath()+"'");
		int filesize = (int) source.length();

		try {
			//If the file is small enough, upload it to one url
			if(filesize <= ContentConnector.MIN_PART_SIZE) {
				Log.i(TAG, "Source is <= 5MB, uploading directly.");
				String uploadUrl = contentConn.getUploadUrl(name);

				byte[] buffer = new byte[filesize];
				try (BufferedInputStream in = new BufferedInputStream( Files.newInputStream(source.toPath()) )) {
					int bytesRead = in.read(buffer);
					String ETag = contentConn.uploadToUrl(buffer, uploadUrl);
				}
				Log.i(TAG, "Direct upload complete!");
			}
			//Otherwise, we need to multipart upload
			else {
				Log.i(TAG, "Source is > 5MB, uploading via multipart.");

				//Get the individual components needed for a multipart upload
				Pair<UUID, List<Uri>> multipart = contentConn.initializeMultipart(name, filesize);
				UUID uploadID = multipart.first;
				List<Uri> uris = multipart.second;


				//Upload the file in parts to each url, receiving an ETag for each one
				List<ContentConnector.ETag> ETags = new ArrayList<>();
				try (BufferedInputStream in = new BufferedInputStream( Files.newInputStream(source.toPath()) )) {
					//WARNING: For if this code is converted to parallel, each loop uses 5MB of memory for the buffer
					for(int i = 0; i < uris.size(); i++) {
						int remaining = filesize - (ContentConnector.MIN_PART_SIZE * i);
						int partSize = Math.min(ContentConnector.MIN_PART_SIZE, remaining);

						byte[] buffer = new byte[partSize];
						int bytesRead = in.read(buffer);

						String uri = uris.get(i).toString();
						String ETag = contentConn.uploadToUrl(buffer, uri);

						ETags.add(new ContentConnector.ETag(i+1, ETag));
					}
				}


				//Confirm the multipart upload is completed, passing the information we've gathered thus far
				contentConn.completeMultipart(name, uploadID, ETags);
				Log.i(TAG, "Multipart upload complete!");
			}


			//Now that the data has been written, create a new entry in the content table
			return contentConn.putProps(name, filesize);

		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------


	public List<SJournal> getJournalEntriesAfter(int journalID) throws ConnectException {
		Log.i(TAG, String.format("GET SERVER JOURNAL ENTRIES called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return journalConn.getJournalEntriesAfter(journalID);
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<SJournal> getJournalEntriesForFile(UUID fileUID) throws ConnectException {
		Log.i(TAG, String.format("GET SERVER JOURNAL ENTRIES FOR FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return journalConn.getJournalEntriesForFile(fileUID);
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws ConnectException, TimeoutException, SocketTimeoutException {
		Log.i(TAG, String.format("LONGPOLL SERVER JOURNAL ENTRIES called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return journalConn.longpollJournalEntriesAfter(journalID);
		} catch (ConnectException e) {
			throw e;
		} catch (TimeoutException | SocketException e) {
			throw new TimeoutException();
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}



	//---------------------------------------------------------------------------------------------

	//TODO Figure out how to log timeouts
	private static class LogInterceptor implements Interceptor {
		@NonNull
		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			Log.d(TAG, String.format("	OKHTTP: %s --> %s", request.method(), request.url()));
			//if(request.body() != null)	//Need another method to print body, this no worky
				//Log.d(TAG, String.format("OKHTTP: Sending with body - %s", request.body()));

			long t1 = System.nanoTime();
			//Response response = chain.proceed(request);
			Response response = chain
					.withConnectTimeout(chain.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
					.withReadTimeout(chain.readTimeoutMillis(), TimeUnit.MILLISECONDS)
					.withWriteTimeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS).proceed(request);
			long t2 = System.nanoTime();

			Log.d(TAG, String.format("	OKHTTP: Received response %s for %s in %.1fms",
					response.code(), response.request().url(), (t2 - t1) / 1e6d));

			//Log.v(TAG, String.format("%s", response.headers()));
			if(response.body() != null)
				Log.d(TAG, "	OKHTTP: Returned with body length of "+response.body().contentLength());
			else
				Log.d(TAG, "	OKHTTP: Returned with null body");

			return response;
		}
	}



	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}
}
