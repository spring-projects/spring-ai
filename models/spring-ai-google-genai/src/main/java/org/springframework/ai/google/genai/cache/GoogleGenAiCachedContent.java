/*
 * Copyright 2023-present the original author or authors.
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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.CachedContent;
import com.google.genai.types.CachedContentUsageMetadata;
import com.google.genai.types.Content;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Represents cached content in Google GenAI for reusing large contexts across multiple
 * requests.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GoogleGenAiCachedContent {

	@JsonProperty("name")
	@Nullable private final String name;

	@JsonProperty("model")
	@Nullable private final String model;

	@JsonProperty("display_name")
	@Nullable private final String displayName;

	@JsonProperty("create_time")
	@Nullable private final Instant createTime;

	@JsonProperty("update_time")
	@Nullable private final Instant updateTime;

	@JsonProperty("expire_time")
	@Nullable private final Instant expireTime;

	@JsonProperty("ttl")
	@Nullable private final Duration ttl;

	@JsonProperty("contents")
	@Nullable private final List<Content> contents;

	@JsonProperty("system_instruction")
	@Nullable private final Content systemInstruction;

	@JsonProperty("usage_metadata")
	@Nullable private final CachedContentUsageMetadata usageMetadata;

	private GoogleGenAiCachedContent(Builder builder) {
		this.name = builder.name;
		this.model = builder.model;
		this.displayName = builder.displayName;
		this.createTime = builder.createTime;
		this.updateTime = builder.updateTime;
		this.expireTime = builder.expireTime;
		this.ttl = builder.ttl;
		this.contents = builder.contents;
		this.systemInstruction = builder.systemInstruction;
		this.usageMetadata = builder.usageMetadata;
	}

	/**
	 * Creates a GoogleGenAiCachedContent from the SDK's CachedContent.
	 * @param cachedContent the SDK cached content
	 * @return a new GoogleGenAiCachedContent instance
	 */
	public static @Nullable GoogleGenAiCachedContent from(@Nullable CachedContent cachedContent) {
		if (cachedContent == null) {
			return null;
		}

		Builder builder = builder().name(cachedContent.name().orElse(null))
			.model(cachedContent.model().orElse(null))
			.displayName(cachedContent.displayName().orElse(null))
			.createTime(cachedContent.createTime().orElse(null))
			.updateTime(cachedContent.updateTime().orElse(null))
			.expireTime(cachedContent.expireTime().orElse(null));

		// Note: ttl, contents, and systemInstruction are not available in the SDK's
		// CachedContent
		// These would be set during creation via CreateCachedContentConfig
		cachedContent.usageMetadata().ifPresent(builder::usageMetadata);

		return builder.build();
	}

	public @Nullable String getName() {
		return this.name;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public @Nullable String getDisplayName() {
		return this.displayName;
	}

	public @Nullable Instant getCreateTime() {
		return this.createTime;
	}

	public @Nullable Instant getUpdateTime() {
		return this.updateTime;
	}

	public @Nullable Instant getExpireTime() {
		return this.expireTime;
	}

	public @Nullable Duration getTtl() {
		return this.ttl;
	}

	public @Nullable List<Content> getContents() {
		return this.contents;
	}

	public @Nullable Content getSystemInstruction() {
		return this.systemInstruction;
	}

	public @Nullable CachedContentUsageMetadata getUsageMetadata() {
		return this.usageMetadata;
	}

	/**
	 * Checks if the cached content has expired.
	 * @return true if expired, false otherwise
	 */
	public boolean isExpired() {
		if (this.expireTime == null) {
			return false;
		}
		return Instant.now().isAfter(this.expireTime);
	}

	/**
	 * Gets the remaining time to live for the cached content.
	 * @return the remaining TTL, or null if no expiration
	 */
	public @Nullable Duration getRemainingTtl() {
		if (this.expireTime == null) {
			return null;
		}
		Duration remaining = Duration.between(Instant.now(), this.expireTime);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	@Override
	public String toString() {
		return "GoogleGenAiCachedContent{" + "name='" + this.name + '\'' + ", model='" + this.model + '\''
				+ ", displayName='" + this.displayName + '\'' + ", expireTime=" + this.expireTime + ", ttl=" + this.ttl
				+ ", isExpired=" + isExpired() + '}';
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		@Nullable private String name;

		@Nullable private String model;

		@Nullable private String displayName;

		@Nullable private Instant createTime;

		@Nullable private Instant updateTime;

		@Nullable private Instant expireTime;

		@Nullable private Duration ttl;

		@Nullable private List<Content> contents;

		@Nullable private Content systemInstruction;

		@Nullable private CachedContentUsageMetadata usageMetadata;

		private Builder() {
		}

		public Builder name(@Nullable String name) {
			this.name = name;
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder displayName(@Nullable String displayName) {
			this.displayName = displayName;
			return this;
		}

		public Builder createTime(@Nullable Instant createTime) {
			this.createTime = createTime;
			return this;
		}

		public Builder updateTime(@Nullable Instant updateTime) {
			this.updateTime = updateTime;
			return this;
		}

		public Builder expireTime(@Nullable Instant expireTime) {
			this.expireTime = expireTime;
			return this;
		}

		public Builder ttl(@Nullable Duration ttl) {
			this.ttl = ttl;
			return this;
		}

		public Builder contents(@Nullable List<Content> contents) {
			this.contents = contents;
			return this;
		}

		public Builder systemInstruction(@Nullable Content systemInstruction) {
			this.systemInstruction = systemInstruction;
			return this;
		}

		public Builder usageMetadata(@Nullable CachedContentUsageMetadata usageMetadata) {
			this.usageMetadata = usageMetadata;
			return this;
		}

		public GoogleGenAiCachedContent build() {
			Assert.hasText(this.model, "Model must not be empty");
			return new GoogleGenAiCachedContent(this);
		}

	}

}
