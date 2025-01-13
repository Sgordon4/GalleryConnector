package com.example.galleryconnector.repositories.combined.jobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

//CAUTION: Only one instance of this class should be made for a given persistLocation!

//This class is a persisted ordered queue
//Adding an item that already exists will resolve the added operations with existing operations
public abstract class PersistedMapQueue<K, V> {
	private final Path persistLocation;
	private final Map<K, V> pendingItems;
	private final ReentrantLock lock;


	public PersistedMapQueue(Path persistLocation) {
		this.persistLocation = persistLocation;
		this.pendingItems = new LinkedHashMap<>();
		this.lock = new ReentrantLock();

		//Read the persisted queue into the map
		readFromFile();		//(No need for a lock here, this is the constructor)
	}

	public abstract K parseKey(String keyString);
	public abstract V parseVal(String valString);


	//---------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return pendingItems.isEmpty();
	}
	public Set<Map.Entry<K, V>> entrySet() {
		return pendingItems.entrySet();
	}
	public V get(K key) {
		return pendingItems.get(key);
	}
	public V getOrDefault(K key, V defaultValue) {
		return pendingItems.getOrDefault(key, defaultValue);
	}
	public boolean containsKey(K key) {
		return pendingItems.containsKey(key);
	}

	public int size() {
		return pendingItems.size();
	}


	//---------------------------------------------------------------------------------------------

	public List<Map.Entry<K, V>> pop(int items) {
		try {
			lock.lock();

			//Grab the next n items (if available)
			Iterator<Map.Entry<K, V>> iterator = pendingItems.entrySet().iterator();
			List<Map.Entry<K, V>> next = new ArrayList<>();
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


	public void enqueue(K key, V val) {
		enqueue(Collections.singletonMap(key, val));
	}
	public void enqueue(Map<K, V> items) {
		try {
			lock.lock();

			//Add the items to the set, and write the changes to disk
			pendingItems.putAll(items);
			persistQueue();

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}


	public void dequeue(K key) {
		dequeue(Collections.singletonList(key));
	}
	public void dequeue(List<K> items) {
		try {
			lock.lock();

			//Remove the items from the set, and write the changes to disk
			items.forEach(pendingItems::remove);
			persistQueue();

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}


	public void clear() {
		try {
			lock.lock();
			pendingItems.clear();
			persistQueue();

		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}


	//---------------------------------------------------------------------------------------------

	//Note: These methods should be used alongside a lock

	private void readFromFile() {
		pendingItems.clear();

		//If the file doesn't exist, we don't have anything queued up
		if(!Files.exists(persistLocation))
			return;

		try {
			//Read all lines from the file
			List<String> lines = Files.readAllLines(persistLocation);

			//Parse each line into a UUID::Integer pair, and add it to the map, preserving the ordering
			for(String line : lines) {
				String[] parts = line.split("::");
				K key = parseKey(parts[0]);
				V val = parseVal(parts[1]);

				pendingItems.put(key, val);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void persistQueue() throws IOException {
		//Make sure the file exists
		if(!Files.exists(persistLocation)) {
			Files.createDirectories(persistLocation.getParent());
			Files.createFile(persistLocation);
		}

		// Convert the LinkedHashMap to a List and write it to the file, preserving ordering
		List<String> lines = pendingItems.entrySet().stream()
				.map(entry -> entry.getKey()+"::"+entry.getValue())
				.collect(Collectors.toList());

		Files.write(persistLocation, lines);
	}
}
