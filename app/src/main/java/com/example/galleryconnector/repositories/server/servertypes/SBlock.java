package com.example.galleryconnector.repositories.server.servertypes;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Date;

public class SBlock {
	@NonNull
	public String blockhash;
	public int blocksize;
	public Instant createtime;


	public SBlock(@NonNull String blockhash, int blocksize) {
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
