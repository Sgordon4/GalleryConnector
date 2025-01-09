package com.example.galleryconnector.repositories.combined.handlers;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.combined.WriteStalling;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.UUID;

public class FileHandler {

	private final WriteStalling writeStalling;


	public static FileHandler getInstance() {
		return FileHandler.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final FileHandler INSTANCE = new FileHandler();
	}
	private FileHandler() {
		writeStalling = WriteStalling.getInstance();
	}


	//---------------------------------------------------------------------------------------------
	// Properties
	//---------------------------------------------------------------------------------------------


	protected GFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException, ConnectException {
		throw new RuntimeException("Stub!");
	}


	//---------------------------------------------------------------------------------------------
	// Content
	//---------------------------------------------------------------------------------------------









}
