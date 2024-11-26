package com.example.galleryconnector.repositories.server.types;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

import java.util.Date;

public class SBlock {
	@NonNull
	public String blockhash;
	public int blocksize;
	public long createtime;


	public SBlock(@NonNull String blockhash, int blocksize) {
		this.blockhash = blockhash;
		this.blocksize = blocksize;
		this.createtime = new Date().getTime();
	}
}
