package com.example.galleryconnector.extra;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.galleryconnector.repositories.local.LocalRepo;
import com.example.galleryconnector.repositories.local.content.LContent;
import com.example.galleryconnector.repositories.local.content.LContentHandler;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class ImportExportApi {
	private static final String TAG = "Gal.IOAPI";
	private final LocalRepo localRepo;


	public static ImportExportApi getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final ImportExportApi INSTANCE = new ImportExportApi();
	}
	private ImportExportApi() {
		localRepo = LocalRepo.getInstance();
	}



	//Import a file to the local system from a uri.
	//Upon a successful import, the file will be moved between local/server based on its parent.
	public LFile importFileToLocal(@NonNull UUID accountuid, @NonNull UUID parent, @NonNull String fileHash, @NonNull Uri source) throws IOException {
		//Import the content to the local repository
		LContent contentProps = localRepo.writeContents(fileHash, source);

		//Make a brand new file entity, and update its content info
		LFile file = new LFile(accountuid);
		file.filehash = contentProps.name;
		file.filesize = contentProps.size;

		file.changetime = Instant.now().getEpochSecond();
		file.modifytime = Instant.now().getEpochSecond();

		//Write the new file entity to the database
		localRepo.putFileProps(file, null, null);



		//Now that the file is imported, we need to add it to the directory
		//TODO Add the new file to parent's ordering


		//Take note of the directory's preferred domain (l, l+s, or s). Then, queue this new file for that.



		return file;
	}


	//Should this also do work on server? Probably, right?
	public JsonObject exportFileFromLocal(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		throw new RuntimeException("Stub!");
	}


	//---------------------------------------------------------------------------------------------


	/*

	private final String IMPORT_GROUP = "import";
	private final String EXPORT_GROUP = "export";

	//Note: External links are not imported to the system, and should not be handled with this method.
	// Instead, their link file should be created and edited through the file creation/edit modals.


	//Launch a WorkManager to import an external uri to the system.
	public void importFile(@NonNull Uri source, @NonNull UUID accountuid, @NonNull UUID parent) {
		//Compile the information we'll need for the import
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "IMPORT");
		builder.putString("TARGET_URI", source.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("ACCOUNTUID", accountuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(IMPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}

	public void exportFile(@NonNull UUID fileuid, @NonNull UUID parent, @NonNull Uri destination) {
		//Compile the information we'll need for the export
		Data.Builder builder = new Data.Builder();
		builder.putString("OPERATION", "EXPORT");
		builder.putString("TARGET_URI", destination.toString());
		builder.putString("PARENTUID", parent.toString());
		builder.putString("FILEUID", fileuid.toString());

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ImportExportWorker.class)
				.setInputData(builder.build())
				.build();

		//Create the work request that will handle the import
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

		//Add the work request to the queue so that the imports run in order
		WorkContinuation continuation = workManager.beginUniqueWork(EXPORT_GROUP, ExistingWorkPolicy.APPEND, request);
		continuation.enqueue();
	}
	 */
}
