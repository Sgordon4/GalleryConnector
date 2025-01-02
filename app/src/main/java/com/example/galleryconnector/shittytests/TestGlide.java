package com.example.galleryconnector.shittytests;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.galleryconnector.MyApplication;
import com.example.galleryconnector.R;
import com.example.galleryconnector.repositories.combined.ContentsNotFoundException;
import com.example.galleryconnector.repositories.combined.GalleryRepo;
import com.example.galleryconnector.repositories.combined.combinedtypes.GFile;
import com.example.galleryconnector.repositories.combined.domain.DomainAPI;
import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.server.ServerRepo;
import com.example.galleryconnector.repositories.server.connectors.ContentConnector;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class TestGlide {
	GalleryRepo grepo = GalleryRepo.getInstance();
	LocalRepo lrepo = LocalRepo.getInstance();
	ServerRepo srepo = ServerRepo.getInstance();
	DomainAPI domainAPI = DomainAPI.getInstance();

	UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	UUID fileUID = UUID.fromString("d79bee5d-1666-4d18-ae29-1bfba6bf0564");

	Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	Uri externalUri_15MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-15mb.jpeg");
	Path tempFileSmall = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");
	Path tempFileLarge = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "largeFile.txt");



	public void displayImageWithGlide(ImageView view) throws FileNotFoundException, ConnectException, ContentsNotFoundException {
		System.out.println("Getting Uri -----------------------------------------------------");

		GFile fileProps = grepo.getFileProps(fileUID);
		if(fileProps.filehash == null)
			throw new RuntimeException();

		//Get a Uri for the file
		//Uri contentUri = grepo.getContentUri(fileProps.filehash);
		//Uri contentUri = lrepo.getContentUri(fileProps.filehash);
		Uri contentUri = srepo.getContentDownloadUri(fileProps.filehash);

		Handler mainThreadHandler = new Handler(Looper.getMainLooper());
		mainThreadHandler.post(() -> {
			Glide.with(view)
					.asBitmap()
					.load(contentUri)
					.into(view);
		});
	}



	public void displayImage(ImageView view) throws FileNotFoundException, ConnectException, ContentsNotFoundException {
		System.out.println("Getting Uri -----------------------------------------------------");

		GFile fileProps = grepo.getFileProps(fileUID);
		if(fileProps.filehash == null)
			throw new RuntimeException();

		//Get a Uri for the file
		//Uri contentUri = grepo.getContentUri(fileProps.filehash);
		//Uri contentUri = lrepo.getContentUri(fileProps.filehash);
		Uri contentUri = srepo.getContentDownloadUri(fileProps.filehash);


		//And put the contents into our testing ImageView
		view.post(() -> {
			System.out.println("Setting Uri -----------------------------------------------------");
			view.setImageURI(contentUri);
			System.out.println("Finished displaying");
		});
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	public void importToBothRepos() throws IOException {
		String smallHash = importSmallTempFile();
		String largeHash = importLargeTempFile();

		int fileSize = grepo.putContentsLocal(smallHash, Uri.fromFile(tempFileSmall.toFile()));
		GFile local = new GFile(fileUID, accountUID);
		local.filehash = smallHash;
		local.filesize = fileSize;
		local = grepo.putFilePropsLocal(local);

		domainAPI.copyFileToServer(local.toLocalFile(), "null", "null");
	}


	//Returns the filehash
	private String importSmallTempFile() throws IOException {
		if(!tempFileSmall.toFile().exists()) {
			Files.createDirectories(tempFileSmall.getParent());
			Files.createFile(tempFileSmall);
		}

		URL smallUrl = new URL(externalUri_1MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(smallUrl.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFileSmall.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			return ContentConnector.bytesToHex( dis.getMessageDigest().digest() );
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}


	//Returns the filehash
	private String importLargeTempFile() throws IOException {
		if(!tempFileLarge.toFile().exists()) {
			Files.createDirectories(tempFileLarge.getParent());
			Files.createFile(tempFileLarge);
		}

		URL smallUrl = new URL(externalUri_15MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(smallUrl.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFileLarge.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			return ContentConnector.bytesToHex( dis.getMessageDigest().digest() );
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
