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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.bedrock.converse.api.BedrockCacheTtl;

public class BedrockCacheProperties {

	private @Nullable BedrockCacheStrategy strategy;

	private @Nullable BedrockCacheTtl ttl;

	public @Nullable BedrockCacheStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(@Nullable BedrockCacheStrategy strategy) {
		this.strategy = strategy;
	}

	public @Nullable BedrockCacheTtl getTtl() {
		return this.ttl;
	}

	public void setTtl(@Nullable BedrockCacheTtl ttl) {
		this.ttl = ttl;
	}

	public BedrockCacheOptions toOptions() {
		return BedrockCacheOptions.builder().strategy(this.strategy).ttl(this.ttl).build();
	}

}
