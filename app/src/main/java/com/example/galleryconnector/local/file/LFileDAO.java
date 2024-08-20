package com.example.galleryconnector.local.file;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.UUID;


/*
For live UI updates, see "Write Observable Queries" in
https://developer.android.com/training/data-storage/room/async-queries#guava-livedata
 */

@Dao
public interface LFileDAO {
	//Mostly for testing
	@Query("SELECT * FROM LFileEntity LIMIT 500")
	List<LFileEntity> loadAll();
	@Query("SELECT * FROM LFileEntity LIMIT 500  OFFSET :offset")
	List<LFileEntity> loadAll(int offset);

	@Query("SELECT * FROM LFileEntity WHERE accountuid IN (:accountuids) LIMIT 500")
	List<LFileEntity> loadAllByAccount(UUID... accountuids);
	@Query("SELECT * FROM LFileEntity WHERE accountuid IN (:accountuids) LIMIT 500 OFFSET :offset")
	List<LFileEntity>loadAllByAccount(int offset, UUID... accountuids);

	@Query("SELECT * FROM LFileEntity WHERE fileuid IN (:fileUIDs)")
	List<LFileEntity> loadByUID(UUID... fileUIDs);



	@Upsert
	List<Long> put(LFileEntity... files);

	//@Insert(onConflict = OnConflictStrategy.IGNORE)
	//ListenableFuture<List<Long>> insert(LFile... files);
	//@Update
	//ListenableFuture<Integer> update(LFile... files);

	@Delete
	Integer delete(LFileEntity... files);
	@Query("DELETE FROM LFileEntity WHERE fileuid IN (:fileUIDs)")
	Integer delete(UUID... fileUIDs);
}