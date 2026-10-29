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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpException;
import org.jspecify.annotations.Nullable;

/**
 * Key-based cursor paging over entries that are already in ascending key order, as the
 * values of a sorted map are. The cursor is the opaque, URL-safe encoding of the last key
 * on the previous page, so pages stay consistent when entries are added or removed
 * between requests.
 *
 * @author Christian Tzolov
 */
final class Pagination {

	private static final String PREFIX = "k:";

	private Pagination() {
	}

	/**
	 * Returns the page of {@code entries} following {@code cursor}, with every entry
	 * mapped through {@code item}, in a single pass.
	 * @param entries the entries in ascending {@code key} order
	 * @param pageSize {@code null} returns everything on one page and treats any cursor
	 * as invalid
	 * @throws McpException {@code -32602} when the cursor is not one this method produced
	 */
	static <S, D> Page<D> page(Collection<S> entries, Function<S, String> key, Function<S, D> item,
			@Nullable String cursor, @Nullable Integer pageSize) {
		if (pageSize == null && cursor != null) {
			throw invalidCursor(cursor);
		}
		String after = cursor != null ? decode(cursor) : null;
		List<D> items = new ArrayList<>(pageSize != null ? Math.min(pageSize, entries.size()) : entries.size());
		String last = null;
		boolean more = false;
		for (S entry : entries) {
			String entryKey = key.apply(entry);
			if (after != null && entryKey.compareTo(after) <= 0) {
				continue;
			}
			if (pageSize != null && items.size() == pageSize) {
				more = true;
				break;
			}
			items.add(item.apply(entry));
			last = entryKey;
		}
		return new Page<>(List.copyOf(items), more && last != null ? encode(last) : null);
	}

	static String encode(String lastKey) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString((PREFIX + lastKey).getBytes(StandardCharsets.UTF_8));
	}

	static String decode(String cursor) {
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!decoded.startsWith(PREFIX)) {
				throw invalidCursor(cursor);
			}
			return decoded.substring(PREFIX.length());
		}
		catch (IllegalArgumentException e) {
			throw invalidCursor(cursor);
		}
	}

	private static McpException invalidCursor(String cursor) {
		return McpException.invalidParams("Invalid cursor: " + cursor);
	}

	record Page<D>(List<D> items, @Nullable String nextCursor) {
	}

}
