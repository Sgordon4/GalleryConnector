package com.example.galleryconnector.repositories.server.servertypes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class SAccount {
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

	public long logintime;
	public long changetime;
	public long createtime;


	public SAccount(){
		this(UUID.randomUUID(), UUID.randomUUID());
	}
	public SAccount(@NonNull UUID accountuid, @NonNull UUID rootfileuid) {
		this.accountuid = accountuid;
		this.rootfileuid = rootfileuid;

		this.email = null;
		this.displayname = null;
		this.password = null;

		this.isdeleted = false;

		this.logintime = -1;
		this.changetime = -1;
		this.createtime = new Date().getTime();
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
		SAccount sAccount = (SAccount) o;
		return isdeleted == sAccount.isdeleted && logintime == sAccount.logintime && changetime == sAccount.changetime && createtime == sAccount.createtime && Objects.equals(accountuid, sAccount.accountuid) && Objects.equals(rootfileuid, sAccount.rootfileuid) && Objects.equals(email, sAccount.email) && Objects.equals(displayname, sAccount.displayname) && Objects.equals(password, sAccount.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountuid, rootfileuid, email, displayname, password, isdeleted, logintime, changetime, createtime);
	}
}
