package com.example.galleryconnector.repositories.server.servertypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Ignore;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SFile {
	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	public boolean isdir;
	public boolean islink;
	public boolean isdeleted;

	public int filesize;
	@Nullable
	public String filehash;

	@NonNull
	public JsonObject userattr;
	@Nullable
	public String attrhash;

	@NonNull
	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	@NonNull
	public Long createtime;



	public SFile(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public SFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
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
		SFile sFile = (SFile) o;
		return isdir == sFile.isdir && islink == sFile.islink &&
				isdeleted == sFile.isdeleted && filesize == sFile.filesize &&
				Objects.equals(fileuid, sFile.fileuid) && Objects.equals(accountuid, sFile.accountuid) &&
				Objects.equals(filehash, sFile.filehash) && Objects.equals(userattr, sFile.userattr) &&
				Objects.equals(attrhash, sFile.attrhash) && Objects.equals(changetime, sFile.changetime) &&
				Objects.equals(modifytime, sFile.modifytime) && Objects.equals(accesstime, sFile.accesstime) &&
				Objects.equals(createtime, sFile.createtime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, isdeleted, filesize, filehash,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}
}
