package com.example.galleryconnector.local.block;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

@Dao
public interface LBlockDao {
	@Query("SELECT * FROM block LIMIT 500")
	ListenableFuture<List<LBlock>> loadAll();
	@Query("SELECT * FROM block LIMIT 500 OFFSET :offset")
	ListenableFuture<List<LBlock>> loadAll(int offset);

	@Query("SELECT * FROM block WHERE blockhash IN (:blockHashes)")
	ListenableFuture<List<LBlock>> loadAllByHash(String... blockHashes);


	@Insert(onConflict = OnConflictStrategy.IGNORE)
	ListenableFuture<List<Long>> insert(LBlock... blocks);

	@Update
	ListenableFuture<Integer> update(LBlock... blocks);

	@Delete
	ListenableFuture<Integer> delete(LBlock... blocks);
}