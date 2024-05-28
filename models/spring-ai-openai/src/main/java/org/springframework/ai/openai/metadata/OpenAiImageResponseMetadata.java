/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.openai.metadata;

import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Objects;

public class OpenAiImageResponseMetadata extends HashMap<String, Object> implements ImageResponseMetadata {

	private final Long created;

	public static OpenAiImageResponseMetadata from(OpenAiImageApi.OpenAiImageResponse openAiImageResponse) {
		Assert.notNull(openAiImageResponse, "OpenAiImageResponse must not be null");
		return new OpenAiImageResponseMetadata(openAiImageResponse.created());
	}

	protected OpenAiImageResponseMetadata(Long created) {
		this.created = created;
	}

	@Override
	public Long getCreated() {
		return this.created;
	}

	@Override
	public String toString() {
		return "OpenAiImageResponseMetadata{" + "created=" + created + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OpenAiImageResponseMetadata that))
			return false;
		return Objects.equals(created, that.created);
	}

	@Override
	public int hashCode() {
		return Objects.hash(created);
	}

}
