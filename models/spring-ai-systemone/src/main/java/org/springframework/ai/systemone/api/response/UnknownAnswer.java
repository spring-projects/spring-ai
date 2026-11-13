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

package org.springframework.ai.systemone.api.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * An answer whose {@code type} this client version does not model. The raw JSON is
 * preserved so a new primitive of the protocol does not break an existing application:
 * the call still succeeds and the unrecognised answer can be read out of {@link #raw()}.
 *
 * @param typeName the {@code type} value as it arrived, or {@code null} when absent
 * @param raw the whole answer object as parsed JSON
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record UnknownAnswer(@Nullable String typeName, Map<String, Object> raw) implements Answer {

	public UnknownAnswer {
		// Not Map.copyOf: it rejects null values, and an unmodelled answer is exactly the
		// place a JSON null may turn up. Failing here would defeat the fallback.
		raw = raw == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
	}

	@Override
	public AnswerType type() {
		return AnswerType.UNKNOWN;
	}
}
