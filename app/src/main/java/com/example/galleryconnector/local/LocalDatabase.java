package com.example.galleryconnector.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.galleryconnector.local.account.LAccount;
import com.example.galleryconnector.local.account.LAccountDAO;
import com.example.galleryconnector.local.block.LBlock;
import com.example.galleryconnector.local.block.LBlockDao;
import com.example.galleryconnector.local.file.LFile;
import com.example.galleryconnector.local.file.LFileDAO;
import com.example.galleryconnector.local.journal.LJournal;
import com.example.galleryconnector.local.journal.LJournalDao;

import java.util.UUID;


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

							LFile a = new LFile(UUID.randomUUID());


							//---------------------------------------------------------------------
							//Journal triggers

							//When a file row is inserted or updated, add a record to the Journal.
							//The journal inserts themselves are identical, there are just two triggers for insert and update respectively
							db.execSQL("CREATE TRIGGER IF NOT EXISTS file_insert_to_journal AFTER INSERT ON file FOR EACH ROW "+
									"BEGIN "+
										"INSERT INTO journal (accountuid, fileuid, isdir, islink, fileblocks, filesize, filehash, isdeleted) " +
										"VALUES (NEW.accountuid, NEW.fileuid, NEW.isdir, NEW.islink, NEW.fileblocks, NEW.filesize, NEW.filehash, NEW.isdeleted); "+
									"END;");
							db.execSQL("CREATE TRIGGER IF NOT EXISTS file_update_to_journal AFTER UPDATE OF accountuid, isdir, islink, fileblocks, filesize, filehash, isdeleted " +
									"ON file FOR EACH ROW "+
									"BEGIN "+
										"INSERT INTO journal (fileuid, accountuid, isdir, islink, fileblocks, filesize, filehash, isdeleted) " +
										"VALUES (NEW.fileuid, NEW.accountuid, NEW.isdir, NEW.islink, NEW.fileblocks, NEW.filesize, NEW.filehash, NEW.isdeleted); "+
									"END;");

							//Note: No DELETE trigger, since to 'delete' a file we actually set the isdeleted bit.
							// Actual row deletion would be the result of admin work like scheduled cleanup or a file domain move (local -> server, vice versa).

						}
					}).build();
		}
	}
}