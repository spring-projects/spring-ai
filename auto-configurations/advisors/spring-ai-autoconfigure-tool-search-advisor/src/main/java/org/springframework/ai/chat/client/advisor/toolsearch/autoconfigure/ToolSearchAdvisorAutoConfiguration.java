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

import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.eviction.CompositeEvictionStrategy;
import org.springframework.ai.tool.toolsearch.eviction.LruEvictionStrategy;
import org.springframework.ai.tool.toolsearch.eviction.ToolIndexEvictionStrategy;
import org.springframework.ai.tool.toolsearch.eviction.TtlEvictionStrategy;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for
 * {@link ToolSearchToolCallingAdvisor}.
 * <p>
 * When {@code spring.ai.chat.client.tool-search-advisor.enabled=true} this configuration
 * registers a {@link ToolSearchToolCallingAdvisor.Builder} bean typed as
 * {@link ToolCallingAdvisor.Builder}. Because {@code ChatClientAutoConfiguration} guards
 * its own builder bean with {@code @ConditionalOnMissingBean}, the subtype registered
 * here satisfies that condition and the default {@link ToolCallingAdvisor} is replaced
 * transparently.
 * <p>
 * When no {@link ToolIndex} bean is declared by the application, one is registered
 * automatically. {@link RegexToolIndex} is the default when
 * {@code spring.ai.chat.client.tool-search-advisor.tool-index-type} is not set. Set the
 * property explicitly to switch implementations:
 * <ul>
 * <li>{@code tool-index-type=regex} — lightweight regex/keyword matching; no additional
 * dependencies (default).</li>
 * <li>{@code tool-index-type=lucene} — Apache Lucene keyword search; requires
 * {@code org.apache.lucene:lucene-core} on the classpath.</li>
 * <li>{@code tool-index-type=vector} — embedding-based semantic search; requires a
 * {@link VectorStore} bean and {@code spring-ai-vector-store} on the classpath.</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@AutoConfiguration(beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@ConditionalOnClass(ToolSearchToolCallingAdvisor.class)
@EnableConfigurationProperties(ToolSearchAdvisorProperties.class)
@ConditionalOnProperty(prefix = ToolSearchAdvisorProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class ToolSearchAdvisorAutoConfiguration implements InitializingBean {

	private static final Log logger = LogFactory.getLog(ToolSearchAdvisorAutoConfiguration.class);

	private static final Set<String> KNOWN_TOOL_INDEX_TYPES = Set.of("regex", "lucene", "vector");

	private final ToolSearchAdvisorProperties properties;

	public ToolSearchAdvisorAutoConfiguration(ToolSearchAdvisorProperties properties) {
		this.properties = properties;
	}

	@Override
	public void afterPropertiesSet() {
		String type = this.properties.getToolIndexType();
		if (type != null && !KNOWN_TOOL_INDEX_TYPES.contains(type.toLowerCase())) {
			logger.warn("Unknown 'spring.ai.chat.client.tool-search-advisor.tool-index-type' value: '" + type
					+ "'. Known built-in values are: " + KNOWN_TOOL_INDEX_TYPES
					+ ". If this is intentional, ensure a matching ToolIndex bean is declared in the application context.");
		}
	}

	/**
	 * Registers a {@link ToolSearchToolCallingAdvisor.Builder} typed as
	 * {@link ToolCallingAdvisor.Builder}. The broader declared return type ensures that
	 * {@code ChatClientAutoConfiguration#toolCallingAdvisorBuilder} is skipped due to its
	 * {@code @ConditionalOnMissingBean}.
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ ToolCallingManager.class, ToolIndex.class })
	ToolCallingAdvisor.Builder<?> toolCallingAdvisorBuilder(ToolSearchAdvisorProperties properties,
			ToolCallingManager toolCallingManager, ToolIndex toolIndex,
			ObjectProvider<ToolExecutionEligibilityChecker> toolExecutionEligibilityChecker) {

		var builder = ToolSearchToolCallingAdvisor.builder()
			.toolCallingManager(toolCallingManager)
			.toolIndex(toolIndex)
			.advisorOrder(properties.getAdvisorOrder())
			.streamToolCallResponses(properties.isStreamToolCallResponses())
			.referenceToolNameAccumulation(properties.isReferenceToolNameAccumulation())
			.sessionIdKeyName(properties.getSessionIdKeyName())
			.evictionStrategy(buildEvictionStrategy(properties.getEviction()));

		if (properties.getMaxResults() != null) {
			builder.maxResults(properties.getMaxResults());
		}
		if (StringUtils.hasText(properties.getSystemMessageSuffix())) {
			builder.systemMessageSuffix(properties.getSystemMessageSuffix());
		}

		toolExecutionEligibilityChecker.ifAvailable(builder::toolExecutionEligibilityChecker);

		return builder;
	}

	private static ToolIndexEvictionStrategy buildEvictionStrategy(ToolSearchAdvisorProperties.Eviction eviction) {
		LruEvictionStrategy lru = new LruEvictionStrategy(eviction.getLruMaxSessions());
		if (eviction.getTtl() != null) {
			return new CompositeEvictionStrategy(lru, new TtlEvictionStrategy(eviction.getTtl()));
		}
		return lru;
	}

	/**
	 * Registers a {@link VectorToolIndex}.
	 * <p>
	 * Activated only when {@code tool-index-type=vector} is set explicitly. Fails fast
	 * with a descriptive error if no {@link VectorStore} bean is present in the context.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(VectorToolIndex.class)
	@ConditionalOnMissingBean(ToolIndex.class)
	@ConditionalOnProperty(prefix = ToolSearchAdvisorProperties.CONFIG_PREFIX, name = "tool-index-type",
			havingValue = "vector", matchIfMissing = false)
	static class VectorToolIndexConfiguration {

		@Bean
		VectorToolIndex vectorToolIndex(ObjectProvider<VectorStore> vectorStoreProvider) {
			VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
			if (vectorStore == null) {
				throw new IllegalStateException(
						"'spring.ai.chat.client.tool-search-advisor.tool-index-type=vector' requires a VectorStore "
								+ "bean in the application context, but none was found. Add a VectorStore "
								+ "implementation (e.g. spring-ai-starter-vector-store-pgvector) to your "
								+ "dependencies.");
			}
			return new VectorToolIndex(vectorStore);
		}

	}

	/**
	 * Registers a {@link LuceneToolIndex}.
	 * <p>
	 * Activated only when {@code tool-index-type=lucene} is set explicitly. Also requires
	 * {@code LuceneToolIndex} on the classpath (i.e.
	 * {@code org.apache.lucene:lucene-core} present).
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(LuceneToolIndex.class)
	@ConditionalOnMissingBean(ToolIndex.class)
	@ConditionalOnProperty(prefix = ToolSearchAdvisorProperties.CONFIG_PREFIX, name = "tool-index-type",
			havingValue = "lucene", matchIfMissing = false)
	static class LuceneToolIndexConfiguration {

		@Bean
		LuceneToolIndex luceneToolIndex(ToolSearchAdvisorProperties properties) {
			return new LuceneToolIndex(properties.getLucene().getMinScoreThreshold());
		}

	}

	/**
	 * Registers a {@link RegexToolIndex} — the default when no {@code tool-index-type} is
	 * set.
	 * <p>
	 * Activated by {@code tool-index-type=regex} or when the property is absent
	 * ({@code matchIfMissing=true}). Requires no additional dependencies.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(ToolIndex.class)
	@ConditionalOnProperty(prefix = ToolSearchAdvisorProperties.CONFIG_PREFIX, name = "tool-index-type",
			havingValue = "regex", matchIfMissing = true)
	static class RegexToolIndexConfiguration {

		@Bean
		RegexToolIndex regexToolIndex() {
			return new RegexToolIndex();
		}

	}

}
