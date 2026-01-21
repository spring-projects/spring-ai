/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.moderation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelResponse;

/**
 * Represents a response from a moderation process, encapsulating the moderation metadata
 * and the generated content. This class provides access to both the single generation
 * result and a list containing that result, alongside the metadata associated with the
 * moderation response. Designed for flexibility, it allows retrieval of
 * moderation-specific metadata as well as the moderated content.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class ModerationResponse implements ModelResponse<Generation> {

	private final ModerationResponseMetadata moderationResponseMetadata;

	private final @Nullable Generation generation;

	public ModerationResponse(@Nullable Generation generation) {
		this(generation, new ModerationResponseMetadata());
	}

	public ModerationResponse(@Nullable Generation generation, ModerationResponseMetadata moderationResponseMetadata) {
		this.moderationResponseMetadata = moderationResponseMetadata;
		this.generation = generation;
	}

	@Override
	public @Nullable Generation getResult() {
		return this.generation;
	}

	@Override
	public List<Generation> getResults() {
		if (this.generation == null) {
			return Collections.emptyList();
		}
		return List.of(this.generation);
	}

	@Override
	public ModerationResponseMetadata getMetadata() {
		return this.moderationResponseMetadata;
	}

	@Override
	public String toString() {
		return "ModerationResponse{" + "moderationResponseMetadata=" + this.moderationResponseMetadata
				+ ", generations=" + this.generation + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModerationResponse that)) {
			return false;
		}
		return Objects.equals(this.moderationResponseMetadata, that.moderationResponseMetadata)
				&& Objects.equals(this.generation, that.generation);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.moderationResponseMetadata, this.generation);
	}

}
