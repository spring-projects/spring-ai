/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

public class MutableResponseMetadata implements ResponseMetadata {

	private final Map<String, Object> map = new ConcurrentHashMap<>();

	/**
	 * Puts an element to the context.
	 * @param key key
	 * @param object value
	 * @param <T> value type
	 * @return this for chaining
	 */
	public <T> MutableResponseMetadata put(String key, T object) {
		this.map.put(key, object);
		return this;
	}

	/**
	 * Gets an entry from the context. Returns {@code null} when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @return entry or {@code null} if not present
	 */
	@Override
	@Nullable public <T> T get(String key) {
		return (T) this.map.get(key);
	}

	/**
	 * Removes an entry from the context.
	 * @param key key by which to remove an entry
	 * @return the previous value associated with the key, or null if there was no mapping
	 * for the key
	 */
	public Object remove(Object key) {
		return this.map.remove(key);
	}

	/**
	 * Gets an entry from the context. Throws exception when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @throws IllegalArgumentException if not present
	 * @return entry
	 */
	@Override
	public <T> T getRequired(Object key) {
		T object = (T) this.map.get(key);
		if (object == null) {
			throw new IllegalArgumentException("Context does not have an entry for key [" + key + "]");
		}
		return object;
	}

	/**
	 * Checks if context contains a key.
	 * @param key key
	 * @return {@code true} when the context contains the entry with the given key
	 */
	@Override
	public boolean containsKey(Object key) {
		return this.map.containsKey(key);
	}

	/**
	 * Returns an element or default if not present.
	 * @param key key
	 * @param defaultObject default object to return
	 * @param <T> value type
	 * @return object or default if not present
	 */
	@Override
	public <T> T getOrDefault(Object key, T defaultObject) {
		return (T) this.map.getOrDefault(key, defaultObject);
	}

	@Override
	public Set<Map.Entry<String, Object>> entrySet() {
		return Collections.unmodifiableMap(this.map).entrySet();
	}

	public Set<String> keySet() {
		return Collections.unmodifiableSet(this.map.keySet());
	}

	@Override
	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	/**
	 * Returns an element or calls a mapping function if entry not present. The function
	 * will insert the value to the map.
	 * @param key key
	 * @param mappingFunction mapping function
	 * @param <T> value type
	 * @return object or one derived from the mapping function if not present
	 */
	public <T> T computeIfAbsent(String key, Function<Object, ? extends T> mappingFunction) {
		return (T) this.map.computeIfAbsent(key, mappingFunction);
	}

	/**
	 * Clears the entries from the context.
	 */
	public void clear() {
		this.map.clear();
	}

	public Map<String, Object> getRawMap() {
		return this.map;
	}

}
