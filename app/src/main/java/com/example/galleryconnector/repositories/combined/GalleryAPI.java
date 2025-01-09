package com.example.galleryconnector.repositories.combined;

import com.example.galleryconnector.repositories.combined.handlers.AccountHandler;
import com.example.galleryconnector.repositories.combined.handlers.ContentHandler;
import com.example.galleryconnector.repositories.combined.handlers.FileHandler;
import com.example.galleryconnector.repositories.combined.handlers.JournalHandler;

public class GalleryAPI {
	public final AccountHandler accounts;
	public final FileHandler files;
	protected final ContentHandler content;
	protected final JournalHandler journals;


	public static GalleryAPI getInstance() {
		return GalleryAPI.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final GalleryAPI INSTANCE = new GalleryAPI();
	}
	private GalleryAPI() {
		this.accounts = new AccountHandler();
		this.files = FileHandler.getInstance();
		this.content = new ContentHandler();
		this.journals = new JournalHandler();
	}





}
