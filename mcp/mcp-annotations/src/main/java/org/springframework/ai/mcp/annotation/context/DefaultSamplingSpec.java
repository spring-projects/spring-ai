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

package org.springframework.ai.mcp.annotation.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.AudioContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest.ContextInclusionStrategy;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.ModelHint;
import io.modelcontextprotocol.spec.McpSchema.ModelPreferences;
import io.modelcontextprotocol.spec.McpSchema.ResourceLink;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.ModelPreferenceSpec;
import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.SamplingSpec;

/**
 * @author Christian Tzolov
 */
public class DefaultSamplingSpec implements SamplingSpec {

	protected List<SamplingMessage> messages = new ArrayList<>();

	protected ModelPreferences modelPreferences;

	protected String systemPrompt;

	protected Double temperature;

	protected Integer maxTokens;

	protected List<String> stopSequences = new ArrayList<>();

	protected Map<String, Object> metadata = new HashMap<>();

	protected Map<String, Object> meta = new HashMap<>();

	protected ContextInclusionStrategy includeContextStrategy = ContextInclusionStrategy.NONE;

	@Override
	public SamplingSpec message(ResourceLink... content) {
		return this.messageInternal(content);
	}

	@Override
	public SamplingSpec message(EmbeddedResource... content) {
		return this.messageInternal(content);
	}

	@Override
	public SamplingSpec message(AudioContent... content) {
		return this.messageInternal(content);
	}

	@Override
	public SamplingSpec message(ImageContent... content) {
		return this.messageInternal(content);
	}

	@Override
	public SamplingSpec message(TextContent... content) {
		return this.messageInternal(content);
	}

	private SamplingSpec messageInternal(Content... content) {
		this.messages.addAll(List.of(content).stream().map(c -> new SamplingMessage(Role.USER, c)).toList());
		return this;
	}

	@Override
	public SamplingSpec message(SamplingMessage... message) {
		this.messages.addAll(List.of(message));
		return this;
	}

	@Override
	public SamplingSpec modelPreferences(Consumer<ModelPreferenceSpec> modelPreferenceSpec) {
		var modelPreferencesSpec = new DefaultModelPreferenceSpec();
		modelPreferenceSpec.accept(modelPreferencesSpec);

		this.modelPreferences = ModelPreferences.builder()
			.hints(modelPreferencesSpec.modelHints)
			.costPriority(modelPreferencesSpec.costPriority)
			.speedPriority(modelPreferencesSpec.speedPriority)
			.intelligencePriority(modelPreferencesSpec.intelligencePriority)
			.build();
		return this;
	}

	@Override
	public SamplingSpec systemPrompt(String systemPrompt) {
		this.systemPrompt = systemPrompt;
		return this;
	}

	@Override
	public SamplingSpec includeContextStrategy(ContextInclusionStrategy includeContextStrategy) {
		this.includeContextStrategy = includeContextStrategy;
		return this;
	}

	@Override
	public SamplingSpec temperature(Double temperature) {
		this.temperature = temperature;
		return this;
	}

	@Override
	public SamplingSpec maxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
		return this;
	}

	@Override
	public SamplingSpec stopSequences(String... stopSequences) {
		this.stopSequences.addAll(List.of(stopSequences));
		return this;
	}

	@Override
	public SamplingSpec metadata(Map<String, Object> m) {
		this.metadata.putAll(m);
		return this;
	}

	@Override
	public SamplingSpec metadata(String k, Object v) {
		this.metadata.put(k, v);
		return this;
	}

	@Override
	public SamplingSpec meta(Map<String, Object> m) {
		this.meta.putAll(m);
		return this;
	}

	@Override
	public SamplingSpec meta(String k, Object v) {
		this.meta.put(k, v);
		return this;
	}

	public static class DefaultModelPreferenceSpec implements ModelPreferenceSpec {

		private List<ModelHint> modelHints = new ArrayList<>();

		private Double costPriority;

		private Double speedPriority;

		private Double intelligencePriority;

		@Override
		public ModelPreferenceSpec modelHints(String... models) {
			Assert.notNull(models, "Models must not be null");
			this.modelHints.addAll(List.of(models).stream().map(ModelHint::new).toList());
			return this;
		}

		@Override
		public ModelPreferenceSpec modelHint(String modelHint) {
			Assert.notNull(modelHint, "Model hint must not be null");
			this.modelHints.add(new ModelHint(modelHint));
			return this;
		}

		@Override
		public ModelPreferenceSpec costPriority(Double costPriority) {
			this.costPriority = costPriority;
			return this;
		}

		@Override
		public ModelPreferenceSpec speedPriority(Double speedPriority) {
			this.speedPriority = speedPriority;
			return this;
		}

		@Override
		public ModelPreferenceSpec intelligencePriority(Double intelligencePriority) {
			this.intelligencePriority = intelligencePriority;
			return this;
		}

	}

}
