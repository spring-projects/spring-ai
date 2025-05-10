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

package org.springframework.ai.chat.messages;

import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A message of the type 'developer' passed as input. The developer message gives
 * instructions or requests from developers using the API. This role typically provides
 * detailed instructions for the model to follow, such as specific actions or formats.
 */
public class DeveloperMessage extends AbstractMessage {

	public DeveloperMessage(String textContent) {
		this(textContent, Map.of());
	}

	public DeveloperMessage(Resource resource) {
		this(MessageUtils.readResource(resource), Map.of());
	}

	private DeveloperMessage(String textContent, Map<String, Object> metadata) {
		super(MessageType.DEVELOPER, textContent, metadata);
	}

	@Override
	@NonNull
	public String getText() {
		return this.textContent;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DeveloperMessage that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return Objects.equals(this.textContent, that.textContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.textContent);
	}

	@Override
	public String toString() {
		return "DeveloperMessage{" + "textContent='" + this.textContent + '\'' + ", messageType=" + this.messageType
				+ ", metadata=" + this.metadata + '}';
	}

	public DeveloperMessage copy() {
		return new DeveloperMessage(getText(), Map.copyOf(this.metadata));
	}

	public Builder mutate() {
		return new Builder().text(this.textContent).metadata(this.metadata);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		@Nullable
		private String textContent;

		@Nullable
		private Resource resource;

		private Map<String, Object> metadata = new HashMap<>();

		public Builder text(String textContent) {
			this.textContent = textContent;
			return this;
		}

		public Builder text(Resource resource) {
			this.resource = resource;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public DeveloperMessage build() {
			if (StringUtils.hasText(textContent) && resource != null) {
				throw new IllegalArgumentException("textContent and resource cannot be set at the same time");
			}
			else if (resource != null) {
				this.textContent = MessageUtils.readResource(resource);
			}
			return new DeveloperMessage(this.textContent, this.metadata);
		}

	}

}
