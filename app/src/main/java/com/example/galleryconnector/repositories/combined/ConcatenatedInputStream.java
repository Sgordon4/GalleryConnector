package com.example.galleryconnector.repositories.combined;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/*
Thanks ChatGTP!
Use like:

ConcatenatedInputStream concatStream = new ConcatenatedInputStream(listOfStreams);
int byteRead;
while((byteRead = concatStream.read()) != -1) {
	System.out.print((char) byteRead);
}
concatStream.close();

 */



public class ConcatenatedInputStream extends InputStream{
	private final Iterator<InputStream> inputStreamIterator;
	private InputStream currentStream;

	public ConcatenatedInputStream(List<InputStream> inputStreams) {
		this.inputStreamIterator = inputStreams.iterator();
		if (inputStreamIterator.hasNext()) {
			currentStream = inputStreamIterator.next();
		}
	}

	@Override
	public int read() throws IOException {
		while (currentStream != null) {
			int byteRead = currentStream.read();
			if (byteRead != -1) {
				return byteRead;
			}
			// Current stream is exhausted, move to the next stream if available
			if (inputStreamIterator.hasNext()) {
				currentStream = inputStreamIterator.next();
			} else {
				// No more streams to read from
				currentStream = null;
			}
		}
		return -1; // All streams exhausted
	}


	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while (currentStream != null) {
			int bytesRead = currentStream.read(b, off, len);
			if (bytesRead > 0) {
				return bytesRead;
			}
			// Current stream is exhausted, move to the next stream if available
			if (inputStreamIterator.hasNext()) {
				currentStream = inputStreamIterator.next();
			} else {
				// No more streams to read from
				currentStream = null;
			}
		}
		return -1; // All streams exhausted
	}

	@Override
	public long skip(long n) throws IOException {
		long skipped = 0;
		while (currentStream != null && skipped < n) {
			long skipInCurrentStream = currentStream.skip(n - skipped);
			if (skipInCurrentStream > 0) {
				skipped += skipInCurrentStream;
			} else {
				// Current stream is exhausted, move to the next stream if available
				if (inputStreamIterator.hasNext()) {
					currentStream = inputStreamIterator.next();
				} else {
					// No more streams to skip
					currentStream = null;
				}
			}
		}
		return skipped;
	}

	@Override
	public int available() throws IOException {
		int availableBytes = 0;
		while (currentStream != null) {
			availableBytes += currentStream.available();
			if (availableBytes > 0) {
				break;
			}
			// If current stream has no available data, move to the next one
			if (inputStreamIterator.hasNext()) {
				currentStream = inputStreamIterator.next();
			} else {
				// No more streams available
				currentStream = null;
			}
		}
		return availableBytes;
	}

	@Override
	public void close() throws IOException {
		while (currentStream != null) {
			currentStream.close();
			if (inputStreamIterator.hasNext()) {
				currentStream = inputStreamIterator.next();
			} else {
				currentStream = null;
			}
		}
	}
}