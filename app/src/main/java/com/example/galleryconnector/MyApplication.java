package com.example.galleryconnector;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

//This class is only being used to get context in static/singleton locations.
//This process feels like cheating every time lmao.
public class MyApplication extends Application {

	@SuppressLint("StaticFieldLeak")
	private static Context context;

	public void onCreate() {
		super.onCreate();
		MyApplication.context = getApplicationContext();
	}

	public static Context getAppContext() {
		return MyApplication.context;
	}
}