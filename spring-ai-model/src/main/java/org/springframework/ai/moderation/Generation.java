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

import org.springframework.ai.model.ModelResult;

/**
 * The Generation class represents a response from a moderation process. It encapsulates
 * the moderation generation metadata and the moderation object.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class Generation implements ModelResult<Moderation> {

	private static final ModerationGenerationMetadata NONE = new ModerationGenerationMetadata() {
	};

	private ModerationGenerationMetadata moderationGenerationMetadata = NONE;

	private final Moderation moderation;

	public Generation(Moderation moderation) {
		this.moderation = moderation;
	}

	public Generation(Moderation moderation, ModerationGenerationMetadata moderationGenerationMetadata) {
		this.moderation = moderation;
		this.moderationGenerationMetadata = moderationGenerationMetadata;
	}

	public Generation generationMetadata(ModerationGenerationMetadata moderationGenerationMetadata) {
		this.moderationGenerationMetadata = moderationGenerationMetadata;
		return this;
	}

	@Override
	public Moderation getOutput() {
		return this.moderation;
	}

	@Override
	public ModerationGenerationMetadata getMetadata() {
		return this.moderationGenerationMetadata;
	}

	@Override
	public String toString() {
		return "Generation{" + "moderationGenerationMetadata=" + this.moderationGenerationMetadata + ", moderation="
				+ this.moderation + '}';
	}

}
