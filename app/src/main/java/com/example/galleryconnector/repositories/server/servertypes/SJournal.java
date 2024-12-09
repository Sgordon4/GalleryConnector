package com.example.galleryconnector.repositories.server.servertypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SJournal {
	public int journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@NonNull
	public List<String> fileblocks;
	@Nullable
	public String filehash;

	@Nullable
	public String attrhash;

	public Instant changetime;


	public SJournal(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;
		this.fileblocks = new ArrayList<>();
		this.changetime = null;
	}
	public SJournal(@NonNull SFile file) {
		this.fileuid = file.fileuid;
		this.accountuid = file.accountuid;
		this.fileblocks = file.fileblocks;
		this.filehash = file.filehash;
		this.attrhash = file.attrhash;
		this.changetime = file.changetime;
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


	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (object == null || getClass() != object.getClass()) return false;
		SJournal that = (SJournal) object;
		return Objects.equals(fileuid, that.fileuid) && Objects.equals(accountuid, that.accountuid) &&
				Objects.equals(fileblocks, that.fileblocks) && Objects.equals(filehash, that.filehash) &&
				Objects.equals(attrhash, that.attrhash) && Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, fileblocks, filehash, attrhash, changetime);
	}
}
