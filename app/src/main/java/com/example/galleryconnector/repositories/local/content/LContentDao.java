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


	@Query("SELECT * FROM content WHERE name = :blockHash")
	LContent loadByHash(String blockHash);

	@Query("SELECT * FROM content WHERE name IN (:blockHashes)")
	List<LContent> loadAllByHash(String... blockHashes);
	@Query("SELECT * FROM content WHERE name IN (:blockHashes)")
	List<LContent> loadAllByHash(List<String> blockHashes);


	@Upsert
	List<Long> put(LContent... blocks);

	/*
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	List<Long> insert(LContent... blocks);

	@Update
	Integer update(LContent... blocks);
	 */

	@Delete
	Integer delete(LContent... blocks);
	@Query("DELETE FROM content WHERE name = :blockHash")
	Integer delete(String blockHash);
}