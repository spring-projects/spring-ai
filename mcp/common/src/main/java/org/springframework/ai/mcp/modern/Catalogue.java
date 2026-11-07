/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.modern;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * The sorted, concurrent map behind the in-memory repositories: entries keyed by a unique
 * string, added once, removed by key and listed in key order through {@link Pagination}.
 * Shared by the sync and async repository twins, which differ only in the handler type
 * they store and the paradigm they answer in.
 *
 * @param <T> the entry type
 * @author Christian Tzolov
 */
final class Catalogue<T> {

	private final Map<String, T> entries = new ConcurrentSkipListMap<>();

	private final Function<T, String> key;

	private final String kind;

	private final String keyLabel;

	/**
	 * @param key derives the unique key of an entry
	 * @param kind the entry kind for messages, for example {@code "Tool"}
	 * @param keyLabel the key's name for messages, for example {@code "name"}
	 */
	Catalogue(Function<T, String> key, String kind, String keyLabel) {
		this.key = key;
		this.kind = kind;
		this.keyLabel = keyLabel;
	}

	/**
	 * @throws IllegalArgumentException if an entry with the same key exists
	 */
	void put(T entry) {
		Assert.notNull(entry, "entry must not be null");
		String entryKey = this.key.apply(entry);
		if (this.entries.putIfAbsent(entryKey, entry) != null) {
			throw new IllegalArgumentException(
					this.kind + " with " + this.keyLabel + " '" + entryKey + "' already exists");
		}
	}

	boolean remove(String entryKey) {
		return this.entries.remove(entryKey) != null;
	}

	@Nullable T get(String entryKey) {
		return this.entries.get(entryKey);
	}

	Collection<T> values() {
		return this.entries.values();
	}

	/**
	 * The page of entries following {@code cursor}, mapped through {@code item}.
	 */
	<D> Pagination.Page<D> page(Function<T, D> item, @Nullable String cursor, @Nullable Integer pageSize) {
		return Pagination.page(this.entries.values(), this.key, item, cursor, pageSize);
	}

}
