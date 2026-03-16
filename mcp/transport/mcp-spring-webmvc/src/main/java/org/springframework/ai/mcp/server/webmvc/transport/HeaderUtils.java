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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.servlet.function.ServerRequest;

/**
 * Utility class for working with HTTP headers. Internal use only.
 *
 * @author Daniel Garnier-Moiroux
 */
final class HeaderUtils {

	private HeaderUtils() {
	}

	static Map<String, List<String>> collectHeaders(ServerRequest request) {
		return request.headers()
			.asHttpHeaders()
			.headerNames()
			.stream()
			.collect(Collectors.toUnmodifiableMap(String::toLowerCase, name -> request.headers().header(name),
					(l1, l2) -> {
						var merged = new ArrayList<>(l1);
						merged.addAll(l2);
						return Collections.unmodifiableList(merged);
					}));
	}

}
