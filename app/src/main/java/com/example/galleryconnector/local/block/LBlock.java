package com.example.galleryconnector.local.block;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "block")
public class LBlock {
	@PrimaryKey
	@NonNull
	public String blockhash;

	@ColumnInfo(defaultValue = "0")
	public int blocksize;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public long createtime;


	public LBlock(@NonNull String blockhash) {
		this.blockhash = blockhash;
		this.blocksize = 0;
		this.createtime = new Date().getTime();
	}
}