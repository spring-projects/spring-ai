/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.metadata;

import com.google.cloud.vertexai.api.GenerateContentResponse.UsageMetadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 * 
 */
public class VertexAiUsage implements Usage {

	private final UsageMetadata usageMetadata;

	public VertexAiUsage(UsageMetadata usageMetadata) {
		Assert.notNull(usageMetadata, "UsageMetadata must not be null");
		this.usageMetadata = usageMetadata;
	}

	@Override
	public Long getPromptTokens() {
		return Long.valueOf(usageMetadata.getPromptTokenCount());
	}

	@Override
	public Long getGenerationTokens() {
		return Long.valueOf(usageMetadata.getCandidatesTokenCount());
	}

}
