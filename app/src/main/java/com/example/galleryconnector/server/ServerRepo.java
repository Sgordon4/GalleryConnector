package com.example.galleryconnector.server;

import static com.example.galleryconnector.server.connectors.BlockConnector.CHUNK_SIZE;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.galleryconnector.server.connectors.AccountConnector;
import com.example.galleryconnector.server.connectors.BlockConnector;
import com.example.galleryconnector.server.connectors.FileConnector;
import com.example.galleryconnector.server.connectors.JournalConnector;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

		//Upload the blockset for the file. This does nothing to the block db if all blocks already exist.
		Map<String, String> fileHashAndSize = uploadBlockset(source, context);

		//Update the file properties with the hash and size
		fileProps.addProperty("blockset", fileHashAndSize.get("blockset"));
		fileProps.addProperty("filehash", fileHashAndSize.get("filehash"));
		fileProps.addProperty("filesize", fileHashAndSize.get("filesize"));


		//TODO Maybe cache the file? Probably best to be done in GalleryRepo alongside a call to this function


		//Now that the blockset is uploaded, create/update the file metadata
		return fileConn.upsert(fileProps);
	}


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
		commitBlocks(fileHashes, source, context);
		Log.d(TAG, "Successful blockset upload!");


		Map<String, String> fileInfo = new HashMap<>();
		fileInfo.put("blockset", new Gson().toJson(fileHashes));
		fileInfo.put("filehash", filehash);
		fileInfo.put("filesize", String.valueOf(filesize));
		return fileInfo;
	}

	private void commitBlocks(@NonNull List<String> fileHashes, @NonNull Uri source, @NonNull Context context) throws IOException {
		ContentResolver contentResolver = context.getContentResolver();

		List<String> missingBlocks;
		do {
			//Get the list of missing blocks
			missingBlocks = getMissingBlocks(fileHashes);

			//For each missing block (if any)...
			for(String missingBlockHash : missingBlocks) {

				//Go to the correct position in the file
				int index = fileHashes.indexOf(missingBlockHash);
				int blockStart = index * CHUNK_SIZE;

				Log.d(TAG, String.format("BSUpload: Reading block at %s = '%s'", blockStart, missingBlockHash));
				try (InputStream is = contentResolver.openInputStream(source)) {
					//Read the missing block
					is.skip(blockStart);
					byte[] block = new byte[CHUNK_SIZE];
					int read = is.read(block);

					//Trim block if needed (tail of the file, not enough bytes to fill a full block)
					if (read != CHUNK_SIZE) {
						byte[] smallerData = new byte[read];
						System.arraycopy(block, 0, smallerData, 0, read);
						block = smallerData;
					}

					//Upload it
					blockConn.uploadData(missingBlockHash, block);
				}
			}
		} while (!missingBlocks.isEmpty());
	}


	public List<String> getMissingBlocks(List<String> blocks) throws IOException {
		JsonArray existingBlocks = blockConn.getProps(blocks);

		for(JsonElement blockElement : existingBlocks) {
			JsonObject blockProps = blockElement.getAsJsonObject();
			blocks.remove(blockProps.get("blockhash").getAsString());
		}
		return blocks;
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
