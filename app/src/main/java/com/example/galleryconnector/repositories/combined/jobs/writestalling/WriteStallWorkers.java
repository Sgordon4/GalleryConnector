package com.example.galleryconnector.repositories.combined.jobs.writestalling;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class WriteStallWorkers {
	private final ScheduledExecutorService jobExecutor;
	private final Map<UUID, WriteRunnable> jobs;
	private final ReentrantLock jobLock;


	public static WriteStallWorkers getInstance() {
		return WriteStallWorkers.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final WriteStallWorkers INSTANCE = new WriteStallWorkers();
	}
	private WriteStallWorkers() {
		jobExecutor = Executors.newScheduledThreadPool(5);
		jobs = new HashMap<>();
		jobLock = new ReentrantLock();
	}



	public void startJobs() {
		WriteStalling writeStalling = WriteStalling.getInstance();
		List<UUID> existingStallFiles = writeStalling.listStallFiles();

		for(UUID fileUID : existingStallFiles) {
			launch(fileUID);
		}
	}
	public void launch(@NonNull UUID fileUID) {
		try {
			jobLock.lock();

			//If there is already a job scheduled/running, do nothing
			if(jobs.containsKey(fileUID))
				return;

			//Otherwise make one and store it
			WriteRunnable runner = new WriteRunnable(fileUID, jobExecutor, this::clearJob);
			jobs.put(fileUID, runner);
		}
		finally {
			jobLock.unlock();
		}
	}


	public void stopJob(@NonNull UUID fileUID) {
		try {
			jobLock.lock();
			WriteRunnable runnable = jobs.get(fileUID);
			if(runnable != null) runnable.stop();
		}
		finally {
			jobLock.unlock();
		}
	}


	//Automatically called by runnable when it finishes
	private void clearJob(UUID fileUID) {
		try {
			jobLock.lock();
			jobs.remove(fileUID);
		}
		finally {
			jobLock.unlock();
		}
	}


	//---------------------------------------------------------------------------------------------


	protected static class WriteRunnable implements Runnable {
		private final WriteStalling writeStalling;
		private final UUID fileUID;
		private final onStopCallback callback;

		private final ScheduledFuture<?> future;

		public WriteRunnable(UUID fileUID, ScheduledExecutorService executor, onStopCallback callback) {
			this.writeStalling = WriteStalling.getInstance();
			this.fileUID = fileUID;
			this.callback = callback;

			//Schedule this to run every 5 seconds (plus execution time)
			this.future = executor.scheduleWithFixedDelay(this, 5, 5, TimeUnit.SECONDS);
		}

		@Override
		public void run() {
			//If there is no data to write, we're done here
			if(writeStalling.doesStallFileExist(fileUID))
				stop();

			//Otherwise, attempt to persist the stall file
			writeStalling.persistStalledWrite(fileUID);
		}
		public void stop() {
			future.cancel(true);
			callback.onStop(fileUID);
		}


		public interface onStopCallback {
			void onStop(UUID fileUID);
		};
	}
}
