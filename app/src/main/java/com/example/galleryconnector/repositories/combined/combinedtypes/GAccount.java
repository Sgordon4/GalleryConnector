package com.example.galleryconnector.repositories.combined.combinedtypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class GAccount {
	@NonNull
	public UUID accountuid;
	@NonNull
	public UUID rootfileuid;

	@Nullable
	public String email;
	@Nullable
	public String displayname;
	@Nullable
	public String password;

	public boolean isdeleted;

	public Long logintime;
	public Long changetime;
	public Long createtime;


	public GAccount(){
		this(UUID.randomUUID(), UUID.randomUUID());
	}
	public GAccount(@NonNull UUID accountuid, @NonNull UUID rootfileuid) {
		this.accountuid = accountuid;
		this.rootfileuid = rootfileuid;

		this.email = null;
		this.displayname = null;
		this.password = null;

		this.isdeleted = false;

		this.logintime = null;
		this.changetime = null;
		this.createtime = Instant.now().toEpochMilli();
	}


	public JsonObject toJson() {
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
		GAccount sAccount = (GAccount) o;
		return isdeleted == sAccount.isdeleted && Objects.equals(logintime, sAccount.logintime) &&
				Objects.equals(changetime, sAccount.changetime) && Objects.equals(createtime, sAccount.createtime) &&
				Objects.equals(accountuid, sAccount.accountuid) && Objects.equals(rootfileuid, sAccount.rootfileuid) &&
				Objects.equals(email, sAccount.email) && Objects.equals(displayname, sAccount.displayname) && Objects.equals(password, sAccount.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountuid, rootfileuid, email, displayname, password, isdeleted, logintime, changetime, createtime);
	}
}
