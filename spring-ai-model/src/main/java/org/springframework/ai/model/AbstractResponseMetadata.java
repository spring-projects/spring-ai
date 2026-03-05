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

import org.jspecify.annotations.Nullable;

public class AbstractResponseMetadata {

	/**
	 * AI metadata string format.
	 */
	protected static final String AI_METADATA_STRING = "{ id: %1$s, usage: %2$s, rateLimit: %3$s }";

	/**
	 * Metadata map.
	 */
	protected final Map<String, Object> map = new ConcurrentHashMap<>();

	/**
	 * Create a new {@link AbstractResponseMetadata} instance.
	 */
	public AbstractResponseMetadata() {
	}

	/**
	 * Gets an entry from the context. Returns {@code null} when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @return entry or {@code null} if not present
	 */
	public <T> @Nullable T get(String key) {
		return (T) this.map.get(key);
	}

	/**
	 * Gets an entry from the context. Throws exception when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @return entry
	 * @throws IllegalArgumentException if not present
	 */
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
	public <T> T getOrDefault(Object key, T defaultObject) {
		return (T) this.map.getOrDefault(key, defaultObject);
	}

	public Set<Map.Entry<String, Object>> entrySet() {
		return Collections.unmodifiableMap(this.map).entrySet();
	}

	public Set<String> keySet() {
		return Collections.unmodifiableSet(this.map.keySet());
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}

}
