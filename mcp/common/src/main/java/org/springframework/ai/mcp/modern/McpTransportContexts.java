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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * The transport context convention of the modern Spring AI transports, and accessors for
 * it.
 * <p>
 * Unless an application registers its own {@code McpTransportContextExtractor}, the
 * WebMvc and WebFlux transports store the HTTP request headers under {@link #HEADERS}: a
 * map from lower-cased header name to its values, without the {@code Mcp-*} protocol
 * headers, which the request context exposes already. Repositories, visibility filters
 * and handlers read them through {@link #header(McpRequestContext, String)} and
 * {@link #headers(McpRequestContext)}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class McpTransportContexts {

	/**
	 * Transport context key of the HTTP request headers: a
	 * {@code Map<String, List<String>>} with lower-cased names.
	 */
	public static final String HEADERS = "http.headers";

	private static final String PROTOCOL_HEADER_PREFIX = "mcp-";

	private McpTransportContexts() {
	}

	/**
	 * Builds the default transport context from the request headers. Names are
	 * lower-cased, {@code Mcp-*} headers are dropped.
	 * @param headers hands every header name with its values to the given consumer, the
	 * shape of {@code HttpHeaders::forEach}
	 */
	public static McpTransportContext fromHeaders(Consumer<BiConsumer<String, List<String>>> headers) {
		Assert.notNull(headers, "headers must not be null");
		Map<String, List<String>> copy = new LinkedHashMap<>();
		headers.accept((name, values) -> {
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.startsWith(PROTOCOL_HEADER_PREFIX)) {
				// The same header reported under two casings keeps both sets of values.
				copy.merge(lower, List.copyOf(values), (left, right) -> {
					List<String> merged = new ArrayList<>(left);
					merged.addAll(right);
					return List.copyOf(merged);
				});
			}
		});
		return McpTransportContext.create(Map.of(HEADERS, Map.copyOf(copy)));
	}

	/**
	 * The request headers stored in the transport context, keyed by lower-cased name, or
	 * an empty map when the transport stored none. A custom extractor may store a single
	 * string per header instead of a list; both shapes are read, other values are
	 * ignored.
	 */
	public static Map<String, List<String>> headers(McpTransportContext transportContext) {
		Assert.notNull(transportContext, "transportContext must not be null");
		if (!(transportContext.get(HEADERS) instanceof Map<?, ?> stored)) {
			return Map.of();
		}
		Map<String, List<String>> headers = new LinkedHashMap<>();
		stored.forEach((name, value) -> {
			if (!(name instanceof String headerName)) {
				return;
			}
			if (value instanceof String single) {
				headers.put(headerName.toLowerCase(Locale.ROOT), List.of(single));
			}
			else if (value instanceof List<?> values) {
				headers.put(headerName.toLowerCase(Locale.ROOT), values.stream().map(String::valueOf).toList());
			}
		});
		return Map.copyOf(headers);
	}

	public static Map<String, List<String>> headers(McpRequestContext ctx) {
		Assert.notNull(ctx, "ctx must not be null");
		return headers(ctx.transportContext());
	}

	/**
	 * The first value of the named request header, matched case-insensitively, or
	 * {@code null} when absent.
	 */
	public static @Nullable String header(McpTransportContext transportContext, String name) {
		Assert.hasText(name, "name must not be empty");
		List<String> values = headers(transportContext).get(name.toLowerCase(Locale.ROOT));
		return values == null || values.isEmpty() ? null : values.get(0);
	}

	public static @Nullable String header(McpRequestContext ctx, String name) {
		Assert.notNull(ctx, "ctx must not be null");
		return header(ctx.transportContext(), name);
	}

}
