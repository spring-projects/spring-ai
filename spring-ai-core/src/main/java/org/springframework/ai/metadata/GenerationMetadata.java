/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.metadata;

/**
 * Abstract Data Type (ADT) modeling common AI provider metadata returned in an AI
 * response.
 *
 * @author John Blum
 * @since 0.7.0
 */
public interface GenerationMetadata {

	GenerationMetadata NULL = new GenerationMetadata() {
	};

	/**
	 * Returns AI provider specific metadata on rate limits.
	 * @return AI provider specific metadata on rate limits.
	 * @see RateLimit
	 */
	default RateLimit getRateLimit() {
		return RateLimit.NULL;
	}

	/**
	 * Returns AI provider specific metadata on API usage.
	 * @return AI provider specific metadata on API usage.
	 * @see Usage
	 */
	default Usage getUsage() {
		return Usage.NULL;
	}

}
