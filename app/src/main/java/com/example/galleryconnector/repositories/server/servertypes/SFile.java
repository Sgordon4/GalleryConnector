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

	@NonNull
	public JsonObject userattr;

	@NonNull
	public List<String> fileblocks;
	public int filesize;
	@NonNull
	public String filehash;

	@NonNull
	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	@NonNull
	public Long createtime;

	@Nullable
	public String attrhash;


	public SFile(@NonNull UUID accountuid) {
		this(accountuid, UUID.randomUUID());
	}
	public SFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.isdeleted = false;
		this.userattr = new JsonObject();
		this.fileblocks = new ArrayList<>();
		this.filesize = 0;
		this.filehash = "";
		this.changetime = Instant.now().toEpochMilli();
		this.modifytime = null;
		this.accesstime = null;
		this.createtime = Instant.now().toEpochMilli();
	}



	public String hashAttributes() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.fileuid);
		sb.append(this.accountuid);
		sb.append(this.isdir);
		sb.append(this.islink);
		sb.append(this.isdeleted);
		sb.append(this.userattr);
		sb.append(this.fileblocks);
		sb.append(this.filesize);
		sb.append(this.filehash);
		//sb.append(this.changetime);
		//sb.append(this.modifytime);
		//sb.append(this.accesstime);
		//sb.append(this.createtime);

		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes());
			this.attrhash = bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		return this.attrhash;
	}
	//https://stackoverflow.com/a/9855338
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	private static String bytesToHex(@NonNull byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}




	//We want to exclude some fields with default values from the JSON output



	public JsonObject toJson() {
		return new Gson().toJsonTree(this).getAsJsonObject();
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
		SFile that = (SFile) o;
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
