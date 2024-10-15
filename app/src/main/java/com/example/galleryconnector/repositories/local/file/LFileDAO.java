package com.example.galleryconnector.repositories.local.file;

import androidx.annotation.Nullable;
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
	List<LFileEntity> loadAll();
	@Query("SELECT * FROM file LIMIT 500  OFFSET :offset")
	List<LFileEntity> loadAll(int offset);

	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500")
	List<LFileEntity> loadAllByAccount(UUID... accountuids);
	@Query("SELECT * FROM file WHERE accountuid IN (:accountuids) LIMIT 500 OFFSET :offset")
	List<LFileEntity> loadAllByAccount(int offset, UUID... accountuids);


	@Nullable
	@Query("SELECT * FROM file WHERE fileuid = :fileUID")
	LFileEntity loadByUID(UUID fileUID);
	@Query("SELECT * FROM file WHERE fileuid IN (:fileUIDs)")
	List<LFileEntity> loadByUID(UUID... fileUIDs);



	@Upsert
	List<Long> put(LFileEntity... files);


	@Delete
	Integer delete(LFileEntity... files);
	@Query("DELETE FROM file WHERE fileuid IN (:fileUIDs)")
	Integer delete(UUID... fileUIDs);
}