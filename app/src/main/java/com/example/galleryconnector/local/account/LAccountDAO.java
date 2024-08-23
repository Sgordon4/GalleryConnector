package com.example.galleryconnector.local.account;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.UUID;


@Dao
public interface LAccountDAO {
	@Query("SELECT * FROM account LIMIT 500")
	List<LAccountEntity> loadAll();
	@Query("SELECT * FROM account LIMIT 500 OFFSET :offset")
	List<LAccountEntity> loadAll(int offset);

	@Query("SELECT * FROM account WHERE accountuid IN (:accountUIDs)")
	List<LAccountEntity> loadByUID(UUID... accountUIDs);


	@Upsert
	List<Long> put(LAccountEntity... accounts);

	@Delete
	Integer delete(LAccountEntity account);
}