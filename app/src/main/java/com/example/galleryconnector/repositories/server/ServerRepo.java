package com.example.galleryconnector.repositories.server;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.repositories.combined.ConcatenatedInputStream;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
	// Account
	//---------------------------------------------------------------------------------------------

	public SAccount getAccountProps(@NonNull UUID accountUID) throws IOException {
		Log.i(TAG, String.format("GET ACCOUNT PROPS called with accountUID='%s'", accountUID));

		JsonObject accountProps = accountConn.getProps(accountUID);
		if(accountProps == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return new Gson().fromJson(accountProps, SAccount.class);
	}

	public void putAccountProps(@NonNull SAccount accountProps) throws IOException {
		Log.i(TAG, String.format("PUT ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));

		accountConn.updateEntry(accountProps.toJson());
	}



	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------


	public SFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET FILE called with fileUID='%s'", fileUID));

		try {
			return fileConn.getProps(fileUID);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void putFileProps(@NonNull SFile fileProps) throws IOException {
		Log.i(TAG, String.format("PUT FILE called with fileUID='%s'", fileProps.fileuid));

		//Check if the block repo is missing any blocks from the blockset
		List<String> missingBlocks = fileProps.fileblocks.stream()
				.filter( b -> !getBlockPropsExist(b) )
				.collect(Collectors.toList());


		//If any are missing, we can't commit the file changes
		if(!missingBlocks.isEmpty())
			throw new IllegalStateException("Missing blocks: "+missingBlocks);


		//Now that we've confirmed all blocks exist, create/update the file metadata
		fileConn.upsert(fileProps);

		//TODO Maybe cache the file?
	}



	public InputStream getFileContents(UUID fileUID) throws IOException {
		Log.i(TAG, String.format("GET FILE CONTENTS called with fileUID='%s'", fileUID));

		SFile file = getFileProps(fileUID);
		List<String> blockList = file.fileblocks;

		ContentResolver contentResolver = MyApplication.getAppContext().getContentResolver();
		List<InputStream> blockStreams = new ArrayList<>();
		for(String block : blockList) {
			Uri blockUri = getBlockUri(block);
			blockStreams.add(contentResolver.openInputStream(blockUri)); //TODO Might be null if block doesn't exist
		}

		return new ConcatenatedInputStream(blockStreams);
	}



	//More of a helper method
	//Given a Uri, parse its contents into an evenly chunked set of blocks and write them to disk
	//Find the fileSize and SHA-256 fileHash while we do so.
	public SFile putFileContents(@NonNull UUID fileUID, @NonNull Uri source) throws FileNotFoundException {
		Log.i(TAG, String.format("PUT FILE CONTENTS (Uri) called with fileUID='%s'", fileUID));
		SFile file = getFileProps(fileUID);

		try (InputStream is = MyApplication.getAppContext().getContentResolver().openInputStream(source);
			 DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {

			//Read the next block
			byte[] block = new byte[BlockConnector.CHUNK_SIZE];
			int read;
			while((read = dis.read(block)) != -1) {

				//Trim block if needed (for tail of the file, when not enough bytes to fill a full block)
				if (read != BlockConnector.CHUNK_SIZE) {
					byte[] smallerData = new byte[read];
					System.arraycopy(block, 0, smallerData, 0, read);
					block = smallerData;

					if(block.length == 0)   //Don't put empty blocks in the blocklist
						continue;
				}


				//Write the block to the system
				String hashString = putBlockContents(block);

				//Add to the blockSet
				file.fileblocks.add(hashString);
				file.filesize += block.length;
			}


			//Get the SHA-256 hash of the entire file
			file.filehash = BlockConnector.bytesToHex( dis.getMessageDigest().digest() );

		} catch (IOException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		//Update the file information in the system
		try {
			putFileProps(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return file;
	}


	//TODO This should go in galleryconn, not here. Need to cache the file
	public void downloadFullFile(@NonNull UUID fileUID, @NonNull Uri dest) {

	}


	//---------------------------------------------------------------------------------------------
	// Block
	//---------------------------------------------------------------------------------------------

	public SBlock getBlockProps(@NonNull String blockHash) {
		Log.i(TAG, String.format("GET BLOCK PROPS called with blockHash='%s'", blockHash));

		try {
			return blockConn.getProps(blockHash);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public boolean getBlockPropsExist(@NonNull String blockHash) {
		return getBlockProps(blockHash) != null;
	}


	@Nullable
	public Uri getBlockUri(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("\nGET BLOCK URI called with blockHash='"+blockHash+"'"));
		return Uri.parse(blockConn.getUrl(blockHash));
	}


	@Nullable
	public byte[] getBlockContents(@NonNull String blockHash) throws IOException {
		Log.i(TAG, String.format("GET BLOCK DATA called with blockHash='%s'", blockHash));
		return blockConn.readBlock(blockHash);
	}

	public String putBlockContents(@NonNull byte[] blockData) throws IOException {
		Log.i(TAG, "\nPUT BLOCK CONTENTS BYTE called");
		return blockConn.uploadData(blockData);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------


	public List<SJournal> getJournalEntriesAfter(int journalID) throws IOException {
		Log.i(TAG, String.format("GET JOURNAL ENTRIES called with journalID='%s'", journalID));

		return journalConn.getJournalEntriesAfter(journalID);
	}

	public List<SJournal> getJournalEntriesForFile(UUID fileUID) throws IOException {
		Log.i(TAG, String.format("GET JOURNAL ENTRIES FOR FILE called with fileUID='%s'", fileUID));

		return journalConn.getJournalEntriesForFile(fileUID);
	}

	public List<SJournal> longpollJournalEntriesAfter(int journalID) throws IOException {
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
