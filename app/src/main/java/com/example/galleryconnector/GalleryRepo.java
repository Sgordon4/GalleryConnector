package com.example.galleryconnector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.galleryconnector.local.LocalDatabase;
import com.example.galleryconnector.local.account.LAccount;
import com.example.galleryconnector.server.ServerRepo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class GalleryRepo {

	private static final String TAG = "Gal.GRepo";
	private final ListeningExecutorService executor;

	private LocalDatabase localRepo;
	private ServerRepo serverRepo;


	public static GalleryRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GalleryRepo INSTANCE = new GalleryRepo();
	}
	private GalleryRepo() {
		executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

		localRepo = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );
		serverRepo = ServerRepo.getInstance();
	}



	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	@Nullable
	public ListenableFuture<JsonObject> getAccountProps(@NonNull UUID accountuid) {
		return executor.submit(() -> {
			//Try to get the account data from local. If it exists, return that.
			List<LAccount> localAccountProps = localRepo.getAccountDao().loadByUID(accountuid);
			if(!localAccountProps.isEmpty())
				return new Gson().toJsonTree( localAccountProps.get(0) ).getAsJsonObject();


			//If the account doesn't exist locally, try to get it from the server.
			try {
				return serverRepo.accountConn.getProps(accountuid);
			} catch (SocketTimeoutException e) {
				return null;
			}
		});
	}


}
