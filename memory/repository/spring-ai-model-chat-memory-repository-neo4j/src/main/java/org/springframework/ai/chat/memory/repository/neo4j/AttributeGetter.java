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

package org.springframework.ai.chat.memory.repository.neo4j;

import java.util.Map;

import org.springframework.util.Assert;

/**
 * Convenience interface for retrieving named attributes out of result maps.
 */
interface AttributeGetter {

	/**
	 * Extract and return this required attribute from the provided map, as a String.
	 */
	default String stringFrom(Map<String, Object> map) {
		Object v = map.get(this.getValue());
		Assert.state(v != null, "value for attribute %s was null".formatted(this.getValue()));
		return (String) v;
	}

	/**
	 * Extract and return this required attribute from the provided map, using type
	 * {@code clazz}.
	 */
	default <T> T objectFrom(Map<String, Object> map, Class<T> clazz) {
		Object v = map.get(this.getValue());
		Assert.state(v != null, "value for attribute %s was null".formatted(this.getValue()));
		return clazz.cast(v);
	}

	String getValue();

}
