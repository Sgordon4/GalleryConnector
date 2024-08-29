package com.example.galleryconnector.local.file;

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

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public List<String> fileblocks;
	@ColumnInfo(defaultValue = "0")
	public int filesize;
	@Nullable
	public String filehash;

	@ColumnInfo(defaultValue = "false")
	public boolean isdeleted;

	@ColumnInfo(defaultValue = "-1")
	//Last time the file properties (database row) were changed
	public long changetime;
	@ColumnInfo(defaultValue = "-1")
	//Last time the file contents were modified
	public long modifytime;
	@ColumnInfo(defaultValue = "-1")
	//Last time the file contents were accessed
	public long accesstime;
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Timestamp createtime;


	@Ignore
	public LFileEntity(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public LFileEntity(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.fileblocks = new ArrayList<>();
		this.filesize = 0;
		this.isdeleted = false;
		this.changetime = -1;
		this.modifytime = -1;
		this.accesstime = -1;
		this.createtime = new Timestamp(new Date().getTime());

	}


	//We want to exclude some fields with default values from the JSON output
	@Ignore
	public ExclusionStrategy strategy = new ExclusionStrategy() {
		@Override
		public boolean shouldSkipField(FieldAttributes f) {
			switch (f.getName()) {
				case "isdir": return !isdir;
				case "islink": return !islink;
				case "changetime": return changetime == -1;
				case "modifytime": return modifytime == -1;
				case "accesstime": return accesstime == -1;
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
		LFileEntity file = (LFileEntity) o;
		return isdir == file.isdir && islink == file.islink && filesize == file.filesize && isdeleted == file.isdeleted && changetime == file.changetime && modifytime == file.modifytime && accesstime == file.accesstime && createtime == file.createtime && Objects.equals(fileuid, file.fileuid) && Objects.equals(accountuid, file.accountuid) && Objects.equals(fileblocks, file.fileblocks) && Objects.equals(filehash, file.filehash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, fileblocks, filesize, filehash, isdeleted, changetime, modifytime, accesstime, createtime);
	}
}