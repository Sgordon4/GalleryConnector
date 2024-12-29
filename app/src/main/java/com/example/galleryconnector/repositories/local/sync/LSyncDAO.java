package com.example.galleryconnector.repositories.local.sync;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.galleryconnector.repositories.local.file.LFile;

import java.util.List;
import java.util.UUID;

@Dao
public interface LSyncDAO {
	@Query("SELECT * FROM lastsync where fileUID = :fileUID")
	LSyncFile loadByUID(UUID fileUID);
	@Query("SELECT * FROM lastsync WHERE fileuid IN (:fileUIDs)")
	List<LSyncFile> loadByUID(UUID... fileUIDs);

	@Upsert
	List<Long> put(LSyncFile... files);

	@Delete
	Integer delete(LSyncFile... files);
	@Query("DELETE FROM lastsync WHERE fileuid IN (:fileUIDs)")
	Integer delete(UUID... fileUIDs);
}
