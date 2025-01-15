package com.example.galleryconnector.repositories.local.content;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface LContentDao {
	@Query("SELECT * FROM content LIMIT 500")
	List<LContent> loadAll();
	@Query("SELECT * FROM content LIMIT 500 OFFSET :offset")
	List<LContent> loadAll(int offset);


	@Query("SELECT * FROM content WHERE name = :fileHash")
	LContent loadByHash(String fileHash);

	@Query("SELECT * FROM content WHERE name IN (:fileHashes)")
	List<LContent> loadAllByHash(String... fileHashes);
	@Query("SELECT * FROM content WHERE name IN (:fileHashes)")
	List<LContent> loadAllByHash(List<String> fileHashes);


	@Upsert
	List<Long> put(LContent... contents);

	/*
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	List<Long> insert(LContent... contents);

	@Update
	Integer update(LContent... contents);
	 */

	@Delete
	Integer delete(LContent... contents);
	@Query("DELETE FROM content WHERE name = :fileHash")
	Integer delete(String fileHash);
}