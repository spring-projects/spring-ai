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
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpException;
import org.jspecify.annotations.Nullable;

/**
 * Key-based cursor paging over a list of specifications. The cursor is the opaque,
 * URL-safe encoding of the last key on the previous page, so pages stay consistent when
 * entries are added or removed between requests.
 *
 * @author Christian Tzolov
 */
final class Pagination {

	private static final String PREFIX = "k:";

	private Pagination() {
	}

	/**
	 * Sorts {@code specifications} by key and returns the page following {@code cursor}.
	 * @param pageSize {@code null} returns everything on one page and treats any cursor
	 * as invalid
	 * @throws McpException {@code -32602} when the cursor is not one this method produced
	 */
	static <S> Page<S> page(List<S> specifications, Function<S, String> key, @Nullable String cursor,
			@Nullable Integer pageSize) {
		if (pageSize == null) {
			if (cursor != null) {
				throw invalidCursor(cursor);
			}
			return new Page<>(List.copyOf(specifications), null);
		}
		List<S> sorted = new ArrayList<>(specifications);
		sorted.sort(Comparator.comparing(key));
		int start = 0;
		if (cursor != null) {
			String lastKey = decode(cursor);
			while (start < sorted.size() && key.apply(sorted.get(start)).compareTo(lastKey) <= 0) {
				start++;
			}
		}
		int end = Math.min(start + pageSize, sorted.size());
		List<S> items = List.copyOf(sorted.subList(start, end));
		String next = end < sorted.size() ? encode(key.apply(sorted.get(end - 1))) : null;
		return new Page<>(items, next);
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

	record Page<S>(List<S> items, @Nullable String nextCursor) {
	}

}
