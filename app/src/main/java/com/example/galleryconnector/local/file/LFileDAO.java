package com.example.galleryconnector.local.file;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.UUID;


/*
For live UI updates, see "Write Observable Queries" in
https://developer.android.com/training/data-storage/room/async-queries#guava-livedata
 */

@Dao
public interface LFileDAO {
	//Mostly for testing
	@Query("SELECT * FROM file LIMIT 500")
	ListenableFuture<List<LFile>> loadAll();
	@Query("SELECT * FROM file LIMIT 500  OFFSET :offset")
	ListenableFuture<List<LFile>> loadAll(int offset);

	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500")
	ListenableFuture<List<LFile>> loadAllByAccount(UUID... accountuids);
	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500 OFFSET :offset")
	ListenableFuture<List<LFile>> loadAllByAccount(int offset, UUID... accountuids);

	@Query("SELECT * FROM file WHERE fileuid IN (:fileUIDs)")
	ListenableFuture<List<LFile>> loadByUID(UUID... fileUIDs);



	@Upsert
	ListenableFuture<List<Long>> put(LFile... files);

	//@Insert(onConflict = OnConflictStrategy.IGNORE)
	//ListenableFuture<List<Long>> insert(LFile... files);
	//@Update
	//ListenableFuture<Integer> update(LFile... files);

	@Delete
	ListenableFuture<Integer> delete(LFile... files);
	@Query("DELETE FROM file WHERE fileuid IN (:fileUIDs)")
	ListenableFuture<Integer> delete(UUID... fileUIDs);
}