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

package org.springframework.ai.google.genai.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import org.springframework.util.Assert;

/**
 * Request for creating cached content in Google GenAI.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public final class CachedContentRequest {

	@JsonProperty("model")
	private final String model;

	@JsonProperty("display_name")
	private final String displayName;

	@JsonProperty("contents")
	private final List<Content> contents;

	@JsonProperty("system_instruction")
	private final Content systemInstruction;

	@JsonProperty("ttl")
	private final Duration ttl;

	@JsonProperty("expire_time")
	private final Instant expireTime;

	private CachedContentRequest(Builder builder) {
		Assert.hasText(builder.model, "Model must not be empty");
		Assert.isTrue(builder.contents != null && !builder.contents.isEmpty(), "Contents must not be empty");
		Assert.isTrue(builder.ttl != null || builder.expireTime != null, "Either TTL or expire time must be set");

		this.model = builder.model;
		this.displayName = builder.displayName;
		this.contents = new ArrayList<>(builder.contents);
		this.systemInstruction = builder.systemInstruction;
		this.ttl = builder.ttl;
		this.expireTime = builder.expireTime;
	}

	public String getModel() {
		return this.model;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public List<Content> getContents() {
		return this.contents;
	}

	public Content getSystemInstruction() {
		return this.systemInstruction;
	}

	public Duration getTtl() {
		return this.ttl;
	}

	public Instant getExpireTime() {
		return this.expireTime;
	}

	@Override
	public String toString() {
		return "CachedContentRequest{" + "model='" + this.model + '\'' + ", displayName='" + this.displayName + '\''
				+ ", contentsSize=" + (this.contents != null ? this.contents.size() : 0) + ", ttl=" + this.ttl
				+ ", expireTime=" + this.expireTime + '}';
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String model;

		private String displayName;

		private List<Content> contents = new ArrayList<>();

		private Content systemInstruction;

		private Duration ttl;

		private Instant expireTime;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder displayName(String displayName) {
			this.displayName = displayName;
			return this;
		}

		public Builder contents(List<Content> contents) {
			this.contents = contents != null ? new ArrayList<>(contents) : new ArrayList<>();
			return this;
		}

		public Builder addContent(Content content) {
			if (content != null) {
				this.contents.add(content);
			}
			return this;
		}

		public Builder addTextContent(String text) {
			if (text != null) {
				this.contents.add(Content.builder().parts(Part.builder().text(text).build()).build());
			}
			return this;
		}

		public Builder systemInstruction(Content systemInstruction) {
			this.systemInstruction = systemInstruction;
			return this;
		}

		public Builder systemInstruction(String instruction) {
			if (instruction != null) {
				this.systemInstruction = Content.builder().parts(Part.builder().text(instruction).build()).build();
			}
			return this;
		}

		public Builder ttl(Duration ttl) {
			this.ttl = ttl;
			return this;
		}

		public Builder expireTime(Instant expireTime) {
			this.expireTime = expireTime;
			return this;
		}

		public CachedContentRequest build() {
			return new CachedContentRequest(this);
		}

	}

}
