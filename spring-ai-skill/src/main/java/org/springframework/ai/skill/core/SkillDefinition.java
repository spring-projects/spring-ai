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

import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.skill.common.LoadStrategy;

/**
 * Skill definition containing metadata and loading strategy.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SkillDefinition {

	private final String skillId;

	private final String source;

	private final Supplier<Skill> loader;

	private final LoadStrategy loadStrategy;

	private final SkillMetadata metadata;

	private SkillDefinition(Builder builder) {
		this.skillId = Objects.requireNonNull(builder.skillId, "skillId cannot be null");
		this.source = Objects.requireNonNull(builder.source, "source cannot be null");
		this.loader = Objects.requireNonNull(builder.loader, "loader cannot be null");
		this.loadStrategy = Objects.requireNonNull(builder.loadStrategy, "loadStrategy cannot be null");
		this.metadata = Objects.requireNonNull(builder.metadata, "metadata cannot be null");
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getSkillId() {
		return this.skillId;
	}

	public String getSource() {
		return this.source;
	}

	public Supplier<Skill> getLoader() {
		return this.loader;
	}

	public LoadStrategy getLoadStrategy() {
		return this.loadStrategy;
	}

	public SkillMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}
		SkillDefinition that = (SkillDefinition) o;
		return Objects.equals(this.skillId, that.skillId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.skillId);
	}

	@Override
	public String toString() {
		return "SkillDefinition{" + "skillId='" + this.skillId + '\'' + ", source='" + this.source + '\''
				+ ", loadStrategy=" + this.loadStrategy + ", metadata=" + this.metadata + '}';
	}

	/**
	 * Builder for immutable SkillDefinition objects.
	 */
	public static class Builder {

		private @Nullable String skillId;

		private @Nullable String source;

		private @Nullable Supplier<Skill> loader;

		private LoadStrategy loadStrategy = LoadStrategy.LAZY;

		private @Nullable SkillMetadata metadata;

		private Builder() {
		}

		public Builder skillId(String skillId) {
			this.skillId = skillId;
			return this;
		}

		public Builder source(String source) {
			this.source = source;
			return this;
		}

		public Builder loader(Supplier<Skill> loader) {
			this.loader = loader;
			return this;
		}

		public Builder loadStrategy(LoadStrategy loadStrategy) {
			this.loadStrategy = loadStrategy;
			return this;
		}

		public Builder metadata(SkillMetadata metadata) {
			this.metadata = metadata;
			return this;
		}

		public SkillDefinition build() {
			if (this.skillId == null) {
				throw new IllegalArgumentException("skillId cannot be null");
			}
			if (this.source == null) {
				throw new IllegalArgumentException("source cannot be null");
			}
			if (this.loader == null) {
				throw new IllegalArgumentException("loader cannot be null");
			}
			if (this.loadStrategy == null) {
				throw new IllegalArgumentException("loadStrategy cannot be null");
			}
			if (this.metadata == null) {
				throw new IllegalArgumentException("metadata cannot be null");
			}
			return new SkillDefinition(this);
		}

	}

}
