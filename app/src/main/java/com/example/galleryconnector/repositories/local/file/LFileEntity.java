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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "file")
public class LFileEntity {
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

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public String userattr;

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public List<String> fileblocks;
	@ColumnInfo(defaultValue = "0")
	public int filesize;
	@Nullable
	public String filehash;

	@NonNull
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	//Last time the file properties (database row) were changed
	public Timestamp changetime;
	@Nullable
	//Last time the file contents were modified
	public Timestamp modifytime;
	@Nullable
	//Last time the file contents were accessed
	public Timestamp accesstime;
	@NonNull
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Timestamp createtime;

	@Nullable
	public String attrhash;


	@Ignore
	public LFileEntity(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public LFileEntity(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.isdeleted = false;
		this.userattr = "{}";
		this.fileblocks = new ArrayList<>();
		this.filesize = 0;
		this.changetime = new Timestamp(new Date().getTime());
		this.createtime = new Timestamp(new Date().getTime());

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
		Gson gson = new GsonBuilder()
				.addSerializationExclusionStrategy(strategy)
				.create();

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
		LFileEntity that = (LFileEntity) o;
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