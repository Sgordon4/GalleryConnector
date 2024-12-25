package com.example.galleryconnector.repositories.combined;

import java.io.IOException;

public class DataNotFoundException extends IOException {
	public DataNotFoundException() {
		super("Data not found");
	}

	public DataNotFoundException(String message) {
		super(message);
	}

	public DataNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public DataNotFoundException(Throwable cause) {
		super(cause);
	}
}

