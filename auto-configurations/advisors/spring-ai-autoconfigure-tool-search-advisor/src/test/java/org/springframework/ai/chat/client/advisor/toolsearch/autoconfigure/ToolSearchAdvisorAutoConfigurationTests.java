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

package org.springframework.ai.chat.client.advisor.toolsearch.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.eviction.CompositeEvictionStrategy;
import org.springframework.ai.tool.toolsearch.eviction.LruEvictionStrategy;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ToolSearchAdvisorAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
class ToolSearchAdvisorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolSearchAdvisorAutoConfiguration.class))
		.withBean(ToolCallingManager.class, () -> mock(ToolCallingManager.class));

	// --- Enabled guard ---

	@Test
	void notActivatedByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ToolIndex.class);
			assertThat(context).doesNotHaveBean(ToolCallingAdvisor.Builder.class);
		});
	}

	// --- ToolIndex selection ---

	@Test
	void regexToolIndexIsRegisteredByDefault() {
		this.contextRunner.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(ToolIndex.class);
			assertThat(context.getBean(ToolIndex.class)).isInstanceOf(RegexToolIndex.class);
		});
	}

	@Test
	void regexToolIndexIsRegisteredWhenExplicitlySet() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.tool-index-type=regex")
			.run(context -> assertThat(context.getBean(ToolIndex.class)).isInstanceOf(RegexToolIndex.class));
	}

	@Test
	void luceneToolIndexIsRegisteredWhenPropertySet() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.tool-index-type=lucene")
			.run(context -> assertThat(context.getBean(ToolIndex.class)).isInstanceOf(LuceneToolIndex.class));
	}

	@Test
	void vectorToolIndexIsRegisteredWhenPropertySetAndVectorStoreBeanPresent() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.tool-index-type=vector")
			.withBean(VectorStore.class, () -> mock(VectorStore.class))
			.run(context -> assertThat(context.getBean(ToolIndex.class)).isInstanceOf(VectorToolIndex.class));
	}

	@Test
	void vectorToolIndexIsNotRegisteredWithoutVectorStoreBean() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.tool-index-type=vector")
			.run(context -> assertThat(context).doesNotHaveBean(ToolIndex.class));
	}

	@Test
	void customToolIndexBeanSuppressesAutoConfiguration() {
		ToolIndex customIndex = mock(ToolIndex.class);
		this.contextRunner.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true")
			.withBean(ToolIndex.class, () -> customIndex)
			.run(context -> assertThat(context.getBean(ToolIndex.class)).isSameAs(customIndex));
	}

	// --- Advisor builder ---

	@Test
	void toolSearchAdvisorBuilderIsRegistered() {
		this.contextRunner.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(ToolCallingAdvisor.Builder.class);
			assertThat(context.getBean(ToolCallingAdvisor.Builder.class))
				.isInstanceOf(ToolSearchToolCallingAdvisor.Builder.class);
		});
	}

	@Test
	void advisorOrderPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.advisor-order=42")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(builder.getAdvisorOrder()).isEqualTo(42);
			});
	}

	@Test
	void maxResultsPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.max-results=5")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "maxResults")).isEqualTo(5);
			});
	}

	@Test
	void sessionIdKeyNamePropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.session-id-key-name=mySessionId")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "sessionIdKeyName")).isEqualTo("mySessionId");
			});
	}

	@Test
	void toolExecutionEligibilityCheckerIsWiredWhenPresent() {
		ToolExecutionEligibilityChecker checker = chatResponse -> false;
		this.contextRunner.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true")
			.withBean(ToolExecutionEligibilityChecker.class, () -> checker)
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "toolExecutionEligibilityChecker")).isSameAs(checker);
			});
	}

	// --- Remaining builder properties ---

	@Test
	void streamToolCallResponsesPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.stream-tool-call-responses=true")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "streamToolCallResponses")).isEqualTo(true);
			});
	}

	@Test
	void referenceToolNameAccumulationPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.reference-tool-name-accumulation=false")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "referenceToolNameAccumulation")).isEqualTo(false);
			});
	}

	@Test
	void systemMessageSuffixPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.system-message-suffix=Use the search tool.")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "systemMessageSuffix"))
					.isEqualTo("Use the search tool.");
			});
	}

	@Test
	void luceneMinScoreThresholdPropertyIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.tool-index-type=lucene",
					"spring.ai.chat.client.tool-search-advisor.lucene.min-score-threshold=0.5")
			.run(context -> {
				var index = context.getBean(ToolIndex.class);
				assertThat(index).isInstanceOf(LuceneToolIndex.class);
				assertThat(ReflectionTestUtils.getField(index, "minScoreThreshold")).isEqualTo(0.5f);
			});
	}

	// --- Eviction strategy ---

	@Test
	void defaultEvictionStrategyIsLru() {
		this.contextRunner.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true").run(context -> {
			var builder = context.getBean(ToolCallingAdvisor.Builder.class);
			assertThat(ReflectionTestUtils.getField(builder, "evictionStrategy"))
				.isInstanceOf(LruEvictionStrategy.class);
		});
	}

	@Test
	void ttlPropertyProducesCompositeEvictionStrategy() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.client.tool-search-advisor.enabled=true",
					"spring.ai.chat.client.tool-search-advisor.eviction.ttl=10m")
			.run(context -> {
				var builder = context.getBean(ToolCallingAdvisor.Builder.class);
				assertThat(ReflectionTestUtils.getField(builder, "evictionStrategy"))
					.isInstanceOf(CompositeEvictionStrategy.class);
			});
	}

}
