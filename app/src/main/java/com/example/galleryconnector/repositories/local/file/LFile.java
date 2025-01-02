package com.example.galleryconnector.repositories.local.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "file")
public class LFile {
	@PrimaryKey
	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@ColumnInfo(defaultValue = "false")
	public boolean isdir;
	@ColumnInfo(defaultValue = "false")
	public boolean islink;
	@ColumnInfo(defaultValue = "false")
	public boolean isdeleted;

	@ColumnInfo(defaultValue = "0")
	public int filesize;
	@Nullable
	public String filehash;

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public JsonObject userattr;
	@Nullable
	public String attrhash;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long createtime;



	@Ignore
	public LFile(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public LFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.isdeleted = false;
		this.filesize = 0;
		this.userattr = new JsonObject();
		this.changetime = Instant.now().getEpochSecond();
		this.modifytime = null;
		this.accesstime = null;
		this.createtime = Instant.now().getEpochSecond();

	}



	public JsonObject toJson() {
		//We want to exclude some fields with default values from the JSON output
		ExclusionStrategy strategy = new ExclusionStrategy() {
			@Override
			public boolean shouldSkipField(FieldAttributes f) {
				switch (f.getName()) {
					case "modifytime": return modifytime == null;
					case "accesstime": return accesstime == null;
					default: return false;
				}
			}

			@Override
			public boolean shouldSkipClass(Class<?> clazz) {
				return false;
			}
		};

		Gson gson = new GsonBuilder().addSerializationExclusionStrategy(strategy).create();
		//Gson gson = new GsonBuilder().create();
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
		LFile lFile = (LFile) o;
		return isdir == lFile.isdir && islink == lFile.islink &&
				isdeleted == lFile.isdeleted && filesize == lFile.filesize &&
				Objects.equals(fileuid, lFile.fileuid) && Objects.equals(accountuid, lFile.accountuid) &&
				Objects.equals(filehash, lFile.filehash) && Objects.equals(userattr, lFile.userattr) &&
				Objects.equals(attrhash, lFile.attrhash) && Objects.equals(changetime, lFile.changetime) &&
				Objects.equals(modifytime, lFile.modifytime) && Objects.equals(accesstime, lFile.accesstime) &&
				Objects.equals(createtime, lFile.createtime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, isdeleted, filesize, filehash,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}
}