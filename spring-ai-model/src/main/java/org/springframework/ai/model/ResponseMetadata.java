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

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Interface representing metadata associated with an AI model's response.
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
public interface ResponseMetadata {

	/**
	 * Gets an entry from the context. Returns {@code null} when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @return entry or {@code null} if not present
	 */
	@Nullable <T> T get(String key);

	/**
	 * Gets an entry from the context. Throws exception when entry is not present.
	 * @param key key
	 * @param <T> value type
	 * @throws IllegalArgumentException if not present
	 * @return entry
	 */
	<T> T getRequired(Object key);

	/**
	 * Checks if context contains a key.
	 * @param key key
	 * @return {@code true} when the context contains the entry with the given key
	 */
	boolean containsKey(Object key);

	/**
	 * Returns an element or default if not present.
	 * @param key key
	 * @param defaultObject default object to return
	 * @param <T> value type
	 * @return object or default if not present
	 */
	<T> T getOrDefault(Object key, T defaultObject);

	/**
	 * Returns an element or default if not present.
	 * @param key key
	 * @param defaultObjectSupplier supplier for default object to return
	 * @param <T> value type
	 * @return object or default if not present
	 * @since 1.11.0
	 */
	default <T> T getOrDefault(String key, Supplier<T> defaultObjectSupplier) {
		T value = get(key);
		return value != null ? value : defaultObjectSupplier.get();
	}

	Set<Map.Entry<String, Object>> entrySet();

	Set<String> keySet();

	/**
	 * Returns {@code true} if this map contains no key-value mappings.
	 * @return {@code true} if this map contains no key-value mappings
	 */
	boolean isEmpty();

}
