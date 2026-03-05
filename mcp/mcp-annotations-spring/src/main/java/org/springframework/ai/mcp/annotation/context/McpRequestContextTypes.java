/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.context;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.AudioContent;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest.ContextInclusionStrategy;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.ResourceLink;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.util.Assert;

/**
 * @author Christian Tzolov
 */
public interface McpRequestContextTypes<ET> {

	// --------------------------------------
	// Getters
	// --------------------------------------
	McpSchema.Request request();

	ET exchange();

	String sessionId();

	Implementation clientInfo();

	ClientCapabilities clientCapabilities();

	// TODO: Should we rename it to meta()?
	Map<String, Object> requestMeta();

	McpTransportContext transportContext();

	// --------------------------------------
	// Elicitation
	// --------------------------------------

	interface ElicitationSpec {

		ElicitationSpec message(String message);

		ElicitationSpec meta(Map<String, Object> m);

		ElicitationSpec meta(String k, Object v);

	}

	// --------------------------------------
	// Sampling
	// --------------------------------------

	interface ModelPreferenceSpec {

		ModelPreferenceSpec modelHints(String... models);

		ModelPreferenceSpec modelHint(String modelHint);

		ModelPreferenceSpec costPriority(Double costPriority);

		ModelPreferenceSpec speedPriority(Double speedPriority);

		ModelPreferenceSpec intelligencePriority(Double intelligencePriority);

	}

	// --------------------------------------
	// Sampling
	// --------------------------------------

	interface SamplingSpec {

		SamplingSpec message(ResourceLink... content);

		SamplingSpec message(EmbeddedResource... content);

		SamplingSpec message(AudioContent... content);

		SamplingSpec message(ImageContent... content);

		SamplingSpec message(TextContent... content);

		default SamplingSpec message(String... text) {
			return message(List.of(text).stream().map(t -> new TextContent(t)).toList().toArray(new TextContent[0]));
		}

		SamplingSpec message(SamplingMessage... message);

		SamplingSpec modelPreferences(Consumer<ModelPreferenceSpec> modelPreferenceSpec);

		SamplingSpec systemPrompt(String systemPrompt);

		SamplingSpec includeContextStrategy(ContextInclusionStrategy includeContextStrategy);

		SamplingSpec temperature(Double temperature);

		SamplingSpec maxTokens(Integer maxTokens);

		SamplingSpec stopSequences(String... stopSequences);

		SamplingSpec metadata(Map<String, Object> m);

		SamplingSpec metadata(String k, Object v);

		SamplingSpec meta(Map<String, Object> m);

		SamplingSpec meta(String k, Object v);

	}

	// --------------------------------------
	// Progress
	// --------------------------------------

	interface ProgressSpec {

		ProgressSpec progress(double progress);

		ProgressSpec total(double total);

		ProgressSpec message(String message);

		ProgressSpec meta(Map<String, Object> m);

		ProgressSpec meta(String k, Object v);

		default ProgressSpec percentage(int percentage) {
			Assert.isTrue(percentage >= 0 && percentage <= 100, "Percentage must be between 0 and 100");
			return this.progress(percentage).total(100.0);
		}

	}

	// --------------------------------------
	// Logging
	// --------------------------------------

	interface LoggingSpec {

		LoggingSpec message(String message);

		LoggingSpec logger(String logger);

		LoggingSpec level(LoggingLevel level);

		LoggingSpec meta(Map<String, Object> m);

		LoggingSpec meta(String k, Object v);

	}

}
