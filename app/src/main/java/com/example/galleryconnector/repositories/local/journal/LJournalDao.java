package com.example.galleryconnector.repositories.local.journal;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.UUID;

@Dao
public interface LJournalDao {
	//TODO Should we change this to 'journalid >= :journalID'? Idk, might make slightly more sense (loadFromID)
	@Query("SELECT * FROM journal WHERE journalid > :journalID")
	ListenableFuture<List<LJournalEntity>> loadAllAfterID(long journalID);

	@Query("SELECT * FROM journal WHERE fileuid = :fileUID")
	ListenableFuture<List<LJournalEntity>> loadAllByFileUID(UUID fileUID);

	//Journal is append-only, no need to update
	//TODO Make sure the OnConflict is not just checking for fileUID or something.
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	ListenableFuture<List<Long>> insert(LJournalEntity... entries);

	//Might remove since this is append-only, but it's here to mulligan
	@Delete
	ListenableFuture<Integer> delete(LJournalEntity... entries);
}