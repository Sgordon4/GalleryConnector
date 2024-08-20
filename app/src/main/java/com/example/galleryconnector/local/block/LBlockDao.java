package com.example.galleryconnector.local.block;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface LBlockDao {
	@Query("SELECT * FROM LBlockEntity LIMIT 500")
	List<LBlockEntity> loadAll();
	@Query("SELECT * FROM LBlockEntity LIMIT 500 OFFSET :offset")
	List<LBlockEntity> loadAll(int offset);

	@Query("SELECT * FROM LBlockEntity WHERE blockhash IN (:blockHashes)")
	List<LBlockEntity> loadAllByHash(String... blockHashes);
	@Query("SELECT * FROM LBlockEntity WHERE blockhash IN (:blockHashes)")
	List<LBlockEntity> loadAllByHash(List<String> blockHashes);


	@Upsert
	List<Long> put(LBlockEntity... blocks);

	/*
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	List<Long> insert(LBlock... blocks);

	@Update
	Integer update(LBlock... blocks);
	 */

	@Delete
	Integer delete(LBlockEntity... blocks);
}