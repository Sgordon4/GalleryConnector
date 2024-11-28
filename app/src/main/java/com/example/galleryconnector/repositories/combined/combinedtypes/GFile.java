package com.example.galleryconnector.repositories.combined.combinedtypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Ignore;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class GFile {
	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	public boolean isdir;
	public boolean islink;
	public boolean isdeleted;

	@NonNull
	public JsonObject userattr;

	@NonNull
	public List<String> fileblocks;
	public int filesize;
	@NonNull
	public String filehash;

	public Instant changetime;	//Last time the file properties (database row) were changed
	public Instant modifytime;	//Last time the file contents were modified
	public Instant accesstime;	//Last time the file contents were accessed
	public Instant createtime;

	@Nullable
	public String attrhash;


	public GFile(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public GFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.isdeleted = false;
		this.userattr = new JsonObject();
		this.fileblocks = new ArrayList<>();
		this.filesize = 0;
		this.filehash = "";
		this.changetime = Instant.now();
		this.modifytime = null;
		this.accesstime = null;
		this.createtime = Instant.now();
	}





	//We want to exclude some fields with default values from the JSON output
	@Ignore
	public ExclusionStrategy strategy = new ExclusionStrategy() {
		@Override
		public boolean shouldSkipField(FieldAttributes f) {
			switch (f.getName()) {
				case "modifytime": return modifytime == null;
				case "accesstime": return accesstime == null;
				default:
					return false;
			}
		}

		@Override
		public boolean shouldSkipClass(Class<?> clazz) {
			return false;
		}
	};



	public JsonObject toJson() {
		//Gson gson = new GsonBuilder().addSerializationExclusionStrategy(strategy).create();
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GFile that = (GFile) o;
		return isdir == that.isdir && islink == that.islink && isdeleted == that.isdeleted &&
				filesize == that.filesize && Objects.equals(fileuid, that.fileuid) &&
				Objects.equals(accountuid, that.accountuid) && Objects.equals(userattr, that.userattr) &&
				Objects.equals(fileblocks, that.fileblocks) && Objects.equals(filehash, that.filehash) &&
				Objects.equals(changetime, that.changetime) && Objects.equals(modifytime, that.modifytime) &&
				Objects.equals(accesstime, that.accesstime) && Objects.equals(createtime, that.createtime) &&
				Objects.equals(attrhash, that.attrhash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, isdeleted, userattr,
				fileblocks, filesize, filehash, changetime, modifytime, accesstime, createtime, attrhash);
	}
}
