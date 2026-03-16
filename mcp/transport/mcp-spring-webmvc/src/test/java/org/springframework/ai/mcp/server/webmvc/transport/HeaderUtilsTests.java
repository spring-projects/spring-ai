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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderUtilsTests {

	@Test
	void collectHeaders() {
		ServerRequest request = mock(ServerRequest.class);
		ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("Content-Type", "application/json");
		httpHeaders.add("Authorization", "Bearer token");
		httpHeaders.add("Custom-Header", "value1");
		httpHeaders.add("Custom-Header", "value2");

		when(request.headers()).thenReturn(headers);
		when(headers.asHttpHeaders()).thenReturn(httpHeaders);
		when(headers.header("Content-Type")).thenReturn(List.of("application/json"));
		when(headers.header("Authorization")).thenReturn(List.of("Bearer token"));
		when(headers.header("Custom-Header")).thenReturn(List.of("value1", "value2"));

		Map<String, List<String>> result = HeaderUtils.collectHeaders(request);

		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("content-type", List.of("application/json"));
		assertThat(result).containsEntry("authorization", List.of("Bearer token"));
		assertThat(result).containsEntry("custom-header", List.of("value1", "value2"));
	}

	@Test
	void collectHeadersEmpty() {
		ServerRequest request = mock(ServerRequest.class);
		ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
		HttpHeaders httpHeaders = new HttpHeaders();

		when(request.headers()).thenReturn(headers);
		when(headers.asHttpHeaders()).thenReturn(httpHeaders);

		Map<String, List<String>> result = HeaderUtils.collectHeaders(request);

		assertThat(result).isEmpty();
	}

	@Test
	void collectHeadersMixedCase() {
		ServerRequest request = mock(ServerRequest.class);
		ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
		HttpHeaders httpHeaders = mock(HttpHeaders.class);

		when(request.headers()).thenReturn(headers);
		when(headers.asHttpHeaders()).thenReturn(httpHeaders);

		// Mock headerNames to return mixed case keys
		when(httpHeaders.headerNames()).thenReturn(Set.of("X-Custom", "x-custom"));

		// Mock header values for each key
		when(headers.header("X-Custom")).thenReturn(List.of("one", "two"));
		when(headers.header("x-custom")).thenReturn(List.of("three"));

		Map<String, List<String>> result = HeaderUtils.collectHeaders(request);

		assertThat(result).hasSize(1);
		assertThat(result).containsKey("x-custom");
		assertThat(result.get("x-custom")).containsExactlyInAnyOrder("one", "two", "three");
	}

}
