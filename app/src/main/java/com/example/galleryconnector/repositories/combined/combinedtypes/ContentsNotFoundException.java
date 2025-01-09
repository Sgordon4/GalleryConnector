package com.example.galleryconnector.repositories.combined.combinedtypes;

import java.io.IOException;

public class ContentsNotFoundException extends IOException {
	public ContentsNotFoundException() {
		super("Data not found");
	}

	public ContentsNotFoundException(String message) {
		super(message);
	}

	public ContentsNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public ContentsNotFoundException(Throwable cause) {
		super(cause);
	}
}

