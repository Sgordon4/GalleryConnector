package com.example.galleryconnector.repositories.local.journal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.galleryconnector.repositories.local.file.LFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "journal"
		/*,
		foreignKeys = {
			@ForeignKey(entity = LAccount.class,
			parentColumns = "accountuid",
			childColumns = "accountuid",
			onDelete = ForeignKey.CASCADE),
			@ForeignKey(entity = LFile.class,
			parentColumns = "fileuid",
			childColumns = "fileuid",
			onDelete = ForeignKey.CASCADE)
		}*/)
public class LJournal {
	@PrimaryKey(autoGenerate = true)
	public int journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@Nullable
	public String filehash;
	@Nullable
	public String attrhash;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long changetime;



	public LJournal(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;
		this.changetime = Instant.now().getEpochSecond();
	}
	public LJournal(@NonNull LFile file) {
		this.fileuid = file.fileuid;
		this.accountuid = file.accountuid;
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
		System.out.println("Journal Changetime is "+changetime);
		JsonObject json = toJson();
		return json.toString();
	}


	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (object == null || getClass() != object.getClass()) return false;
		LJournal that = (LJournal) object;
		return Objects.equals(fileuid, that.fileuid) && Objects.equals(accountuid, that.accountuid) &&
				Objects.equals(filehash, that.filehash) && Objects.equals(attrhash, that.attrhash) &&
				Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, filehash, attrhash, changetime);
	}
}