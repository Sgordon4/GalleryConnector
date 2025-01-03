package com.example.galleryconnector.repositories.local.journal;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import java.util.UUID;

@Dao
public interface LJournalDao {
	@Query("SELECT * FROM journal WHERE journalid > :journalID")
	List<LJournal> loadAllAfterID(long journalID);

	@Query("SELECT * FROM journal WHERE journalid > :journalID")
	LiveData<List<LJournal>> longpollAfterID(long journalID);

	@Query("SELECT * FROM journal WHERE fileuid = :fileUID")
	List<LJournal> loadAllByFileUID(UUID fileUID);


	//Journal is automatically inserted to via trigger when the file table is updated
	//Therefore, no insert method is currently provided


	//Might remove since this is append-only, but it's here to mulligan
	@Delete
	Integer delete(LJournal... entries);
}