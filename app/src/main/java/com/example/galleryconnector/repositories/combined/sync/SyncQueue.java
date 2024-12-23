package com.example.galleryconnector.repositories.combined.sync;

import com.example.galleryconnector.MyApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

//This class is a persisted ordered queue
//Adding an item that already exists will do nothing
public class SyncQueue {
	private final Path queuePersistLocation;
	private final Set<UUID> pendingSync;
	private final ReentrantLock lock;


	public static SyncQueue getInstance() {
		return SyncQueue.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final SyncQueue INSTANCE = new SyncQueue();
	}
	private SyncQueue() {
		String appDataDir = MyApplication.getAppContext().getApplicationInfo().dataDir;
		this.queuePersistLocation = Paths.get(appDataDir, "queues", "syncQueue.txt");
		this.pendingSync = new LinkedHashSet<>();
		this.lock = new ReentrantLock();

		//Read the persisted queue into the pendingSync set
		readQueueFromFile();	//(No need for a lock here, this is the constructor)
	}


	//---------------------------------------------------------------------------------------------


	public boolean isEmpty() {
		return pendingSync.isEmpty();
	}

	public List<UUID> getNextItems(int items) {
		try {
			lock.lock();

			//Grab the next n items (if available)
			//This is essentially a queue.pop()
			Iterator<UUID> iterator = pendingSync.iterator();
			List<UUID> next = new ArrayList<>();
			for(int i = 0; i < items; i++) {
				if(iterator.hasNext()) {
					next.add(iterator.next());
					iterator.remove();
				}
			}

			//And write the changes to disk
			persistQueue();

			return next;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	public void enqueue(UUID item) {
		enqueue(Collections.singletonList(item));
	}
	public void enqueue(List<UUID> items) {
		try {
			lock.lock();

			//Add the items to the set, and write the changes to disk
			pendingSync.addAll(items);
			persistQueue();

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	public void dequeue(UUID item) {
		dequeue(Collections.singletonList(item));
	}
	public void dequeue(List<UUID> items) {
		try {
			lock.lock();

			//Remove the items from the set, and write the changes to disk
			items.forEach(pendingSync::remove);
			persistQueue();

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}



	//---------------------------------------------------------------------------------------------

	//Note: These methods should be used alongside a lock

	private void readQueueFromFile() {
		pendingSync.clear();

		//If the file doesn't exist, we don't have anything queued up
		if(!Files.exists(queuePersistLocation))
			return;

		try {
			//Read all lines from the file
			List<String> lines = Files.readAllLines(queuePersistLocation);

			//And add them all to the set, preserving the ordering
			List<UUID> uuids = lines.stream().map(UUID::fromString).collect(Collectors.toList());
			pendingSync.addAll(uuids);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void persistQueue() throws IOException {
		//Make sure the file exists
		if(!Files.exists(queuePersistLocation))
			Files.createFile(queuePersistLocation);

		// Convert the LinkedHashSet to a List and write it to the file
		List<String> lines = pendingSync.stream().map(UUID::toString).collect(Collectors.toList());
		Files.write(queuePersistLocation, lines);
	}
}
