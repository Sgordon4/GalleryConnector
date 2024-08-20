package com.example.galleryconnector.local.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

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
	@ColumnInfo(defaultValue = "[]")
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
	public long createtime;


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
		this.createtime = new Date().getTime();

	}


	@NonNull
	@Override
	public String toString() {
		return "LFile{" +
				"fileuid=" + fileuid +
				", accountuid=" + accountuid +
				", isdir=" + isdir +
				", islink=" + islink +
				", fileblocks=" + fileblocks +
				", filesize=" + filesize +
				", filehash=" + filehash +
				", isdeleted=" + isdeleted +
				'}';
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