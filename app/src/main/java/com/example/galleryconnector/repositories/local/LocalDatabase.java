package com.example.galleryconnector.repositories.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.galleryconnector.repositories.local.account.LAccount;
import com.example.galleryconnector.repositories.local.account.LAccountDAO;
import com.example.galleryconnector.repositories.local.block.LBlock;
import com.example.galleryconnector.repositories.local.block.LBlockDao;
import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.local.file.LFileDAO;
import com.example.galleryconnector.repositories.local.journal.LJournal;
import com.example.galleryconnector.repositories.local.journal.LJournalDao;


@Database(entities = {LAccount.class, LFile.class, LJournal.class, LBlock.class}, version = 1)
@TypeConverters({LocalConverters.class})
public abstract class LocalDatabase extends RoomDatabase {


	public abstract LAccountDAO getAccountDao();
	public abstract LFileDAO getFileDao();
	public abstract LJournalDao getJournalDao();
	public abstract LBlockDao getBlockDao();



	public static class DBBuilder {
		private static final String DB_NAME = "glocal.db";

		public LocalDatabase newInstance(Context context) {
			return Room.databaseBuilder(context, LocalDatabase.class, DB_NAME)
					.addCallback(new Callback() {
						@Override
						public void onCreate(@NonNull SupportSQLiteDatabase db) {
							super.onCreate(db);


							//Journal triggers

							//When a file row is inserted or updated, add a record to the Journal.
							//The journal inserts themselves are identical, there are just two triggers for insert and update respectively
							db.execSQL("CREATE TRIGGER IF NOT EXISTS file_insert_to_journal AFTER INSERT ON file FOR EACH ROW "+
									"BEGIN "+
										"INSERT INTO journal (accountuid, fileuid, filehash, attrhash) " +
										"VALUES (NEW.accountuid, NEW.fileuid, NEW.filehash, NEW.attrhash); "+
									"END;");
							db.execSQL("CREATE TRIGGER IF NOT EXISTS file_update_to_journal AFTER UPDATE OF filehash, attrhash, isdeleted " +
									"ON file FOR EACH ROW "+
									"WHEN (NEW.attrhash != OLD.attrhash) OR (NEW.filehash != OLD.filehash) "+
									"OR (NEW.isdeleted == false AND OLD.isdeleted == true) "+
									"BEGIN "+
										"INSERT INTO journal (accountuid, fileuid, filehash, attrhash) " +
										"VALUES (NEW.accountuid, NEW.fileuid, NEW.filehash, NEW.attrhash); "+
									"END;");

							//Note: No DELETE trigger, since to 'delete' a file we actually set the isdeleted bit.
							// Actual row deletion would be the result of admin work like scheduled cleanup or a file domain move
							// (local -> server, vice versa).

							//TODO Maybe add block usecount triggers here, for when a file is inserted/updated/deleted
							// Also include the SyncTable in that when we make it


						}
					}).build();
		}
	}
}