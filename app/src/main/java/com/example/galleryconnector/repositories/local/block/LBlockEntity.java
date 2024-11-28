package com.example.galleryconnector.repositories.local.block;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Date;

@Entity(tableName = "block")
public class LBlockEntity {
	@PrimaryKey
	@NonNull
	public String blockhash;

	@ColumnInfo(defaultValue = "0")
	public int blocksize;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Instant createtime;


	public LBlockEntity(@NonNull String blockhash, int blocksize) {
		this.blockhash = blockhash;
		this.blocksize = blocksize;
		this.createtime = Instant.now();
	}


	public JsonObject toJson() {
		Gson gson = new GsonBuilder().create();
		return gson.toJsonTree(this).getAsJsonObject();
	}

	@NonNull
	@Override
	public String toString() {
		JsonObject json = toJson();
		return json.toString();
	}
}