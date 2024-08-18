package com.example.galleryconnector.local.account;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.UUID;

@Dao
public interface LAccountDAO {
	@Query("SELECT * FROM account LIMIT 500")
	List<LAccount> loadAll();
	@Query("SELECT * FROM account LIMIT 500 OFFSET :offset")
	List<LAccount> loadAll(int offset);

	@Query("SELECT * FROM account WHERE accountuid IN (:accountUIDs)")
	List<LAccount> loadByUID(UUID... accountUIDs);


	@Upsert
	List<Long> put(LAccount... accounts);

	@Delete
	Integer delete(LAccount account);
}