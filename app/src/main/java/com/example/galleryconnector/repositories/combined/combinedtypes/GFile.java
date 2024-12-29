package com.example.galleryconnector.repositories.combined.combinedtypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Ignore;

import com.example.galleryconnector.repositories.local.file.LFile;
import com.example.galleryconnector.repositories.server.servertypes.SFile;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
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
	public List<String> fileblocks;
	public int filesize;
	@Nullable
	public String filehash;

	@NonNull
	public JsonObject userattr;
	@Nullable
	public String attrhash;

	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	public Long createtime; //Create time :)


	public GFile(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public GFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.isdeleted = false;
		this.fileblocks = new ArrayList<>();
		this.filesize = 0;
		this.userattr = new JsonObject();
		this.changetime = Instant.now().getEpochSecond();
		this.modifytime = null;
		this.accesstime = null;
		this.createtime = Instant.now().getEpochSecond();
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
		return Objects.hash(fileuid, accountuid, isdir, islink, isdeleted, fileblocks, filesize, filehash,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}


	//---------------------------------------------------------------------------------------------


	public static GFile fromLocalFile(@NonNull LFile local) {
		GFile gFile = new GFile(local.fileuid, local.accountuid);
		gFile.isdir = local.isdir;
		gFile.islink = local.islink;
		gFile.isdeleted = local.isdeleted;
		gFile.fileblocks = local.fileblocks;
		gFile.filesize = local.filesize;
		gFile.filehash = local.filehash;
		gFile.userattr = local.userattr;
		gFile.attrhash = local.attrhash;
		gFile.changetime = local.changetime;
		gFile.modifytime = local.modifytime;
		gFile.accesstime = local.accesstime;
		gFile.createtime = local.createtime;

		return gFile;
	}

	public static GFile fromServerFile(@NonNull SFile server) {
		GFile gFile = new GFile(server.fileuid, server.accountuid);
		gFile.isdir = server.isdir;
		gFile.islink = server.islink;
		gFile.isdeleted = server.isdeleted;
		gFile.fileblocks = server.fileblocks;
		gFile.filesize = server.filesize;
		gFile.filehash = server.filehash;
		gFile.userattr = server.userattr;
		gFile.attrhash = server.attrhash;
		gFile.changetime = server.changetime;
		gFile.modifytime = server.modifytime;
		gFile.accesstime = server.accesstime;
		gFile.createtime = server.createtime;

		return gFile;
	}


	public LFile toLocalFile() {
		LFile local = new LFile(fileuid, accountuid);
		local.isdir = isdir;
		local.islink = islink;
		local.isdeleted = isdeleted;
		local.fileblocks = fileblocks;
		local.filesize = filesize;
		local.filehash = filehash;
		local.userattr = userattr;
		local.attrhash = attrhash;
		local.changetime = changetime;
		local.modifytime = modifytime;
		local.accesstime = accesstime;
		local.createtime = createtime;

		return local;
	}

	public SFile toServerFile() {
		SFile server = new SFile(fileuid, accountuid);
		server.isdir = isdir;
		server.islink = islink;
		server.isdeleted = isdeleted;
		server.fileblocks = fileblocks;
		server.filesize = filesize;
		server.filehash = filehash;
		server.userattr = userattr;
		server.attrhash = attrhash;
		server.changetime = changetime;
		server.modifytime = modifytime;
		server.accesstime = accesstime;
		server.createtime = createtime;

		return server;
	}

}
