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

import org.springframework.util.Assert;

/**
 * A simple implementation of {@link ApiKey} that holds an immutable API key value. This
 * implementation is suitable for cases where the API key is static and does not need to
 * be refreshed or rotated.
 *
 * @author Adib Saikali
 * @since 1.0.0
 */
public final class SimpleApiKey implements ApiKey {

	private final String value;

	/**
	 * Create a new SimpleApiKey.
	 * @param value the API key value, must not be null or empty
	 * @throws IllegalArgumentException if value is null or empty
	 */
	public SimpleApiKey(String value) {
		Assert.hasText(value, "API key value must not be null or empty");
		this.value = value;
	}

	@Override
	public String getValue() {
		return this.value;
	}

	@Override
	public String toString() {
		return "SimpleApiKey{value='***'}";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SimpleApiKey that)) {
			return false;
		}
		return this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

}
