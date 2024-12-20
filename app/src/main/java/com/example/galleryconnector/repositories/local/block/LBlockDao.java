package com.example.galleryconnector.repositories.local.block;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface LBlockDao {
	@Query("SELECT * FROM block LIMIT 500")
	List<LBlock> loadAll();
	@Query("SELECT * FROM block LIMIT 500 OFFSET :offset")
	List<LBlock> loadAll(int offset);


	@Query("SELECT * FROM block WHERE blockhash = :blockHash")
	LBlock loadByHash(String blockHash);

	@Query("SELECT * FROM block WHERE blockhash IN (:blockHashes)")
	List<LBlock> loadAllByHash(String... blockHashes);
	@Query("SELECT * FROM block WHERE blockhash IN (:blockHashes)")
	List<LBlock> loadAllByHash(List<String> blockHashes);


	@Upsert
	List<Long> put(LBlock... blocks);

	/*
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	List<Long> insert(LBlock... blocks);

	@Update
	Integer update(LBlock... blocks);
	 */

	@Delete
	Integer delete(LBlock... blocks);
	@Query("DELETE FROM block WHERE blockhash = :blockHash")
	Integer delete(String blockHash);
}