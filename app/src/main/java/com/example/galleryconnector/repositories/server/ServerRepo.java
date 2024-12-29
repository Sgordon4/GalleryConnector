package com.example.galleryconnector.repositories.server;

import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
import com.example.galleryconnector.repositories.combined.DataNotFoundException;
import com.example.galleryconnector.repositories.local.block.LBlockHandler;
import com.example.galleryconnector.repositories.server.connectors.AccountConnector;
import com.example.galleryconnector.repositories.server.connectors.FileConnector;
import com.example.galleryconnector.repositories.server.connectors.JournalConnector;
import com.example.galleryconnector.repositories.server.connectors.BlockConnector;
import com.example.galleryconnector.repositories.server.servertypes.SAccount;
import com.example.galleryconnector.repositories.server.servertypes.SBlock;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.example.galleryconnector.repositories.server.servertypes.SJournal;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


//TODO Eventually change most/all of the serverRepo.blockConn or fileConn or whatever to just the SRepo method

//TODO Do extensive response code testing inside each of the connectors, handling things with the
// appropriate exceptions or empty arrays or whatever

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


	//TODO Perhaps pass along a timeout, maybe just for longpoll?

	public ServerRepo() {
		client = new OkHttpClient().newBuilder()
				.addInterceptor(new LogInterceptor())
				.followRedirects(true)
				.connectTimeout(5, TimeUnit.SECONDS)	//TODO Temporary timeout, prob increase later
				.readTimeout(30, TimeUnit.SECONDS)		//Long timeout for longpolling
				.writeTimeout(5, TimeUnit.SECONDS)
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
			e.printStackTrace();
			throw new RuntimeException();
		}
	}


	public SFile putFileProps(@NonNull SFile fileProps, @Nullable String prevFileHash, @Nullable String prevAttrHash)
			throws DataNotFoundException, IllegalStateException, ConnectException {
		Log.i(TAG, String.format("PUT SERVER FILE called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = fileProps.fileblocks.stream()
				.filter( b -> {
					try {
						return !getBlockPropsExist(b);
					} catch (ConnectException e) {
						throw new RuntimeException(e);
					}
				})
				.collect(Collectors.toList());

		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new DataNotFoundException("Cannot put props, system is missing "+missingBlocks.size()+" blocks!");


		//Now that we've confirmed all blocks exist, create/update the file metadata
		try {
			Log.i(TAG, "All blocks exist, uploading file properties");
			Log.d(TAG, fileProps.toString());
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



	public InputStream getFileContents(UUID fileUID) throws FileNotFoundException, DataNotFoundException, ConnectException {
		Log.i(TAG, String.format("GET SERVER FILE CONTENTS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		SFile file = getFileProps(fileUID);
		List<String> blockList = file.fileblocks;

		//Turn each block into an InputStream
		List<InputStream> blockStreams = new ArrayList<>();
		for(String block : blockList) {
			try {
				Uri blockUri = getBlockContentsUri(block); //TODO Might be null if block doesn't exist
				blockStreams.add(new URL(blockUri.toString()).openStream());
			} catch (ConnectException e) {
				throw e;
			} catch (SocketTimeoutException | SocketException e) {
				throw new ConnectException();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return new ConcatenatedInputStream(blockStreams);
	}



	public static class BlockSet {
		public List<String> blockList = new ArrayList<>();
		public int fileSize = 0;
		public String fileHash = "";
	}

	//Helper method
	//Given a Uri, parse its contents into an evenly chunked set of blocks and write them to disk
	//Find the fileSize and SHA-256 fileHash while we do so.
	public BlockSet putData(@NonNull Uri source) throws IOException {
		Log.i(TAG, String.format("PUT SERVER FILE CONTENTS (Uri) called with Uri='%s'", source));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		BlockSet blockSet = new BlockSet();


		try (InputStream is = new URL(source.toString()).openStream();
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			byte[] block;
			do {
				Log.d(TAG, "Reading...");
				block = dis.readNBytes(BlockConnector.CHUNK_SIZE);
				Log.d(TAG, "Read "+block.length);

				if(block.length == 0)   //Don't put empty blocks in the blocklist
					continue;


				//Write the block to the system
				String hashString = putBlockContents(block);

				//Add to the blockSet
				blockSet.blockList.add(hashString);
				blockSet.fileSize += block.length;

			} while (block.length >= BlockConnector.CHUNK_SIZE);


			//Get the SHA-256 hash of the entire file
			blockSet.fileHash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );
			Log.d(TAG, "File has "+blockSet.blockList.size()+" blocks, with a size of "+blockSet.fileSize+".");

		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {	//Should never happen
			throw new RuntimeException(e);
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		}

		return blockSet;
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

	public SBlock getBlockProps(@NonNull String blockHash) throws FileNotFoundException, ConnectException {
		Log.i(TAG, String.format("GET SERVER BLOCK PROPS called with blockHash='%s'", blockHash));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		SBlock block;
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


	@Nullable
	public Uri getBlockContentsUri(@NonNull String blockHash) throws DataNotFoundException, ConnectException {
		Log.i(TAG, String.format("\nGET SERVER BLOCK URI called with blockHash='"+blockHash+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return Uri.parse(blockConn.getUrl(blockHash));
		} catch (DataNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	@Nullable
	public byte[] getBlockContents(@NonNull String blockHash) throws DataNotFoundException, ConnectException {
		Log.i(TAG, String.format("GET SERVER BLOCK DATA called with blockHash='%s'", blockHash));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return blockConn.readBlock(blockHash);
		} catch (DataNotFoundException e) {
			throw e;
		} catch (ConnectException e) {
			throw e;
		} catch (SocketTimeoutException | SocketException e) {
			throw new ConnectException();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public String putBlockContents(@NonNull byte[] blockData) throws ConnectException, IOException {
		Log.i(TAG, "\nPUT SERVER BLOCK CONTENTS BYTE called");
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return blockConn.uploadData(blockData);
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

	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws ConnectException, TimeoutException {
		Log.i(TAG, String.format("LONGPOLL SERVER JOURNAL ENTRIES called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			return journalConn.longpollJournalEntriesAfter(journalID);
		} catch (ConnectException e) {
			throw e;
		} catch (TimeoutException | SocketTimeoutException | SocketException e) {
			throw new TimeoutException();
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
