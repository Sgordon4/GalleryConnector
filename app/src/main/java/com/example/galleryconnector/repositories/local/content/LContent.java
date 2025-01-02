package com.example.galleryconnector.repositories.local.content;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;

@Entity(tableName = "content")
public class LContent {
	@PrimaryKey
	@NonNull
	public String name;

	@ColumnInfo(defaultValue = "0")
	public int size;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long createtime;

	@ColumnInfo(defaultValue = "0")
	public int usecount;



	public LContent(@NonNull String name, int size) {
		this.name = name;
		this.size = size;
		this.createtime = Instant.now().getEpochSecond();
		this.usecount = 0;
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