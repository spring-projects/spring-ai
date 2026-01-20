/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Skill metadata.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SkillMetadata {

	private final String name;

	private final String description;

	private final String source;

	private final Map<String, Object> extensions;

	public SkillMetadata(String name, String description, String source) {
		this(name, description, source, new HashMap<>());
	}

	public SkillMetadata(String name, String description, String source, Map<String, Object> extensions) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("name is required");
		}
		if (description == null || description.isEmpty()) {
			throw new IllegalArgumentException("description is required");
		}
		if (source == null || source.isEmpty()) {
			throw new IllegalArgumentException("source is required");
		}

		this.name = name;
		this.description = description;
		this.source = source;
		this.extensions = new HashMap<>(extensions);
	}

	// Builder
	public static Builder builder(String name, String description, String source) {
		return new Builder(name, description, source);
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getSource() {
		return this.source;
	}

	public Map<String, Object> getExtensions() {
		return Collections.unmodifiableMap(this.extensions);
	}

	public @Nullable Object getExtension(String key) {
		return this.extensions.get(key);
	}

	public @Nullable <T> T getExtension(String key, Class<T> type) {
		Object value = this.extensions.get(key);
		if (value == null) {
			return null;
		}
		if (!type.isInstance(value)) {
			throw new ClassCastException("Extension '" + key + "' is not of type " + type.getName());
		}
		return type.cast(value);
	}

	public boolean hasExtension(String key) {
		return this.extensions.containsKey(key);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}
		SkillMetadata that = (SkillMetadata) o;
		return Objects.equals(this.name, that.name) && Objects.equals(this.description, that.description)
				&& Objects.equals(this.source, that.source) && Objects.equals(this.extensions, that.extensions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.description, this.source, this.extensions);
	}

	@Override
	public String toString() {
		return "SkillMetadata{" + "name='" + this.name + '\'' + ", description='" + this.description + '\''
				+ ", source='" + this.source + '\'' + ", extensions=" + this.extensions + '}';
	}

	public static class Builder {

		private final String name;

		private final String description;

		private final String source;

		private final Map<String, Object> extensions = new HashMap<>();

		private Builder(String name, String description, String source) {
			if (name == null || name.isEmpty()) {
				throw new IllegalArgumentException("name is required");
			}
			if (description == null || description.isEmpty()) {
				throw new IllegalArgumentException("description is required");
			}
			if (source == null || source.isEmpty()) {
				throw new IllegalArgumentException("source is required");
			}
			this.name = name;
			this.description = description;
			this.source = source;
		}

		public Builder extension(String key, Object value) {
			this.extensions.put(key, value);
			return this;
		}

		public Builder extensions(Map<String, Object> extensions) {
			this.extensions.putAll(extensions);
			return this;
		}

		public SkillMetadata build() {
			return new SkillMetadata(this.name, this.description, this.source, this.extensions);
		}

	}

}
