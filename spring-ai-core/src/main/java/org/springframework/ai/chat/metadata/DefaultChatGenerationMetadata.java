/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.chat.metadata;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultChatGenerationMetadata implements ChatGenerationMetadata {

	private final Map<String, Object> metadata;

	private final String finishReason;

	private final Set<String> contentFilters = new HashSet<>();

	DefaultChatGenerationMetadata(Map<String, Object> metadata, String finishReason, Set<String> contentFilters) {
		this.metadata = metadata;
		this.finishReason = finishReason;
		this.contentFilters.addAll(contentFilters);
	}

	@Override
	public <T> T get(String key) {
		return (T) this.metadata.get(key);
	}

	@Override
	public boolean containsKey(String key) {
		return this.metadata.containsKey(key);
	}

	@Override
	public <T> T getOrDefault(String key, T defaultObject) {
		return containsKey(key) ? get(key) : defaultObject;
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return this.metadata.entrySet();
	}

	@Override
	public Set<String> keySet() {
		return this.metadata.keySet();
	}

	@Override
	public boolean isEmpty() {
		return this.metadata.isEmpty();
	}

	@Override
	public String getFinishReason() {
		return this.finishReason;
	}

	@Override
	public Set<String> getContentFilters() {
		return this.contentFilters;
	}

	@Override
	public int hashCode() {
		return Objects.hash(metadata, finishReason, contentFilters);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		DefaultChatGenerationMetadata other = (DefaultChatGenerationMetadata) obj;
		return Objects.equals(metadata, other.metadata) && Objects.equals(finishReason, other.finishReason)
				&& Objects.equals(contentFilters, other.contentFilters);
	}

}
