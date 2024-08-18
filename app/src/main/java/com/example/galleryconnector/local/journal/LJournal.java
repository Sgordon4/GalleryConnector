package com.example.galleryconnector.local.journal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;
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
	public long journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;


	@ColumnInfo(defaultValue = "false")
	public boolean isdir;
	@ColumnInfo(defaultValue = "false")
	public boolean islink;


	@NonNull
	@ColumnInfo(defaultValue = "[]")
	public List<String> fileblocks;
	@ColumnInfo(defaultValue = "0")
	public int filesize;
	@Nullable
	public String filehash;

	@ColumnInfo(defaultValue = "false")
	public boolean isdeleted;


	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public long changetime;



	public LJournal(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.filesize = 0;
		this.isdir = false;
		this.islink = false;
		this.fileblocks = new ArrayList<>();
		this.isdeleted = false;
		this.changetime = -1;
	}

	@NonNull
	@Override
	public String toString() {
		return "LJournal{" +
				"journalid=" + journalid +
				", fileuid=" + fileuid +
				", accountuid=" + accountuid +
				", isdir=" + isdir +
				", islink=" + islink +
				", fileblocks=" + fileblocks +
				", filesize=" + filesize +
				", filehash=" + filehash +
				", isdeleted=" + isdeleted +
				", changetime=" + changetime +
				'}';
	}
}