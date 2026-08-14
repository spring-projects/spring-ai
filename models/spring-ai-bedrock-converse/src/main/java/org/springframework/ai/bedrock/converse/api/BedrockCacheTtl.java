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

package org.springframework.ai.bedrock.converse.api;

/**
 * AWS Bedrock cache TTL options for specifying how long cached prompts remain valid.
 *
 * @author Yash Rawal
 * @since 2.0.1
 * @see <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock
 * Prompt Caching</a>
 */
public enum BedrockCacheTtl {

	FIVE_MINUTES("5m"),

	ONE_HOUR("1h");

	private final String value;

	BedrockCacheTtl(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
