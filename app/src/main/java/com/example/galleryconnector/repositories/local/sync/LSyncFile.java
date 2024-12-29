package com.example.galleryconnector.repositories.local.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.example.galleryconnector.repositories.local.file.LFile;

import java.util.UUID;

@Entity(tableName = "lastsync")
public class LSyncFile extends LFile {
	@Ignore
	public LSyncFile(@NonNull UUID accountuid) {
		super(accountuid);
	}
	public LSyncFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		super(fileuid, accountuid);
	}

	@Ignore
	public LSyncFile(LFile file) {
		super(file.fileuid, file.accountuid);
		this.isdir = file.isdir;
		this.islink = file.islink;
		this.isdeleted = file.isdeleted;
		this.fileblocks = file.fileblocks;
		this.filesize = file.filesize;
		this.filehash = file.filehash;
		this.userattr = file.userattr;
		this.attrhash = file.attrhash;
		this.changetime = file.changetime;
		this.modifytime = file.modifytime;
		this.accesstime = file.accesstime;
		this.createtime = file.createtime;
	}
}
