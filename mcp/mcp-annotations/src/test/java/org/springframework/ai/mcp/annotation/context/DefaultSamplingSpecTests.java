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

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest.ContextInclusionStrategy;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultSamplingSpec}.
 *
 * @author Christian Tzolov
 */
public class DefaultSamplingSpecTests {

	@Test
	public void testDefaultValues() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		assertThat(spec.messages).isEmpty();
		assertThat(spec.modelPreferences).isNull();
		assertThat(spec.systemPrompt).isNull();
		assertThat(spec.temperature).isNull();
		assertThat(spec.maxTokens).isNull();
		assertThat(spec.stopSequences).isEmpty();
		assertThat(spec.metadata).isEmpty();
		assertThat(spec.meta).isEmpty();
		assertThat(spec.includeContextStrategy).isEqualTo(ContextInclusionStrategy.NONE);
	}

	@Test
	public void testMessageWithTextContent() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		TextContent content = new TextContent("Test message");

		spec.message(content);

		assertThat(spec.messages).hasSize(1);
		assertThat(spec.messages.get(0).role()).isEqualTo(Role.USER);
		assertThat(spec.messages.get(0).content()).isEqualTo(content);
	}

	@Test
	public void testMessageWithMultipleTextContent() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		TextContent content1 = new TextContent("Message 1");
		TextContent content2 = new TextContent("Message 2");

		spec.message(content1, content2);

		assertThat(spec.messages).hasSize(2);
	}

	@Test
	public void testMessageWithSamplingMessage() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		SamplingMessage message = new SamplingMessage(Role.ASSISTANT, new TextContent("Assistant message"));

		spec.message(message);

		assertThat(spec.messages).hasSize(1);
		assertThat(spec.messages.get(0)).isEqualTo(message);
	}

	@Test
	public void testSystemPrompt() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.systemPrompt("System instructions");

		assertThat(spec.systemPrompt).isEqualTo("System instructions");
	}

	@Test
	public void testTemperature() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.temperature(0.7);

		assertThat(spec.temperature).isEqualTo(0.7);
	}

	@Test
	public void testMaxTokens() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.maxTokens(1000);

		assertThat(spec.maxTokens).isEqualTo(1000);
	}

	@Test
	public void testStopSequences() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.stopSequences("STOP", "END");

		assertThat(spec.stopSequences).containsExactly("STOP", "END");
	}

	@Test
	public void testIncludeContextStrategy() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.includeContextStrategy(ContextInclusionStrategy.ALL_SERVERS);

		assertThat(spec.includeContextStrategy).isEqualTo(ContextInclusionStrategy.ALL_SERVERS);
	}

	@Test
	public void testMetadataWithMap() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		Map<String, Object> metadataMap = Map.of("key1", "value1", "key2", "value2");

		spec.metadata(metadataMap);

		assertThat(spec.metadata).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	public void testMetadataWithKeyValue() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.metadata("key", "value");

		assertThat(spec.metadata).containsEntry("key", "value");
	}

	@Test
	public void testMetaWithMap() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		Map<String, Object> metaMap = Map.of("key1", "value1", "key2", "value2");

		spec.meta(metaMap);

		assertThat(spec.meta).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	public void testMetaWithKeyValue() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.meta("key", "value");

		assertThat(spec.meta).containsEntry("key", "value");
	}

	@Test
	public void testModelPreferences() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		spec.modelPreferences(prefs -> {
			prefs.modelHint("gpt-4");
			prefs.costPriority(0.5);
			prefs.speedPriority(0.8);
			prefs.intelligencePriority(0.9);
		});

		assertThat(spec.modelPreferences).isNotNull();
		assertThat(spec.modelPreferences.hints()).hasSize(1);
		assertThat(spec.modelPreferences.costPriority()).isEqualTo(0.5);
		assertThat(spec.modelPreferences.speedPriority()).isEqualTo(0.8);
		assertThat(spec.modelPreferences.intelligencePriority()).isEqualTo(0.9);
	}

	@Test
	public void testFluentInterface() {
		DefaultSamplingSpec spec = new DefaultSamplingSpec();

		McpRequestContextTypes.SamplingSpec result = spec.message(new TextContent("Test"))
			.systemPrompt("System")
			.temperature(0.7)
			.maxTokens(100)
			.stopSequences("STOP")
			.metadata("key", "value")
			.meta("metaKey", "metaValue");

		assertThat(result).isSameAs(spec);
		assertThat(spec.messages).hasSize(1);
		assertThat(spec.systemPrompt).isEqualTo("System");
		assertThat(spec.temperature).isEqualTo(0.7);
		assertThat(spec.maxTokens).isEqualTo(100);
		assertThat(spec.stopSequences).containsExactly("STOP");
		assertThat(spec.metadata).containsEntry("key", "value");
		assertThat(spec.meta).containsEntry("metaKey", "metaValue");
	}

	// ModelPreferenceSpec Tests

	@Test
	public void testModelPreferenceSpecWithNullModelHint() {
		DefaultSamplingSpec.DefaultModelPreferenceSpec spec = new DefaultSamplingSpec.DefaultModelPreferenceSpec();

		assertThatThrownBy(() -> spec.modelHint(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Model hint must not be null");
	}

	@Test
	public void testModelPreferenceSpecWithNullModelHints() {
		DefaultSamplingSpec.DefaultModelPreferenceSpec spec = new DefaultSamplingSpec.DefaultModelPreferenceSpec();

		assertThatThrownBy(() -> spec.modelHints((String[]) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Models must not be null");
	}

}
