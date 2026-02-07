/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.mcp.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Simple {@link HandlerFilterFunction} which records calls made to an MCP server.
 *
 * @author Daniel Garnier-Moiroux
 */
public class McpTestRequestRecordingExchangeFilterFunction implements HandlerFilterFunction {

	private final List<Call> calls = new ArrayList<>();

	@Override
	public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction next) {
		Map<String, String> headers = request.headers()
			.asHttpHeaders()
			.asMultiValueMap()
			.keySet()
			.stream()
			.collect(Collectors.toMap(String::toLowerCase, k -> String.join(",", request.headers().header(k))));

		var cr = request.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
			this.calls.add(new Call(request.method(), headers, body));
			return ServerRequest.from(request).body(body).build();
		});

		return cr.flatMap(next::handle);

	}

	public List<Call> getCalls() {
		return List.copyOf(this.calls);
	}

	public record Call(HttpMethod method, Map<String, String> headers, String body) {

	}

}
