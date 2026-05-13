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

package org.springframework.ai.vectorstore.observation.autoconfigure;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VectorStoreHealthContributorAutoConfiguration}.
 *
 * @author Surya Teja Gorre
 */
class VectorStoreHealthContributorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VectorStoreHealthContributorAutoConfiguration.class));

	@Test
	void healthIndicatorRegisteredForSingleVectorStore() {
		this.contextRunner.withUserConfiguration(SingleVectorStoreConfiguration.class).run(ctx -> {
			assertThat(ctx).hasSingleBean(HealthContributor.class);
			HealthContributor contributor = ctx.getBean("vectorStoreHealthContributor", HealthContributor.class);
			assertThat(contributor).isInstanceOf(HealthIndicator.class);
			Health health = ((HealthIndicator) contributor).health();
			assertThat(health.getStatus()).isEqualTo(Status.UP);
			assertThat(health.getDetails()).containsEntry("vectorStore", "SimpleVectorStore");
		});
	}

	@Test
	void compositeHealthContributorRegisteredForMultipleVectorStores() {
		this.contextRunner.withUserConfiguration(MultipleVectorStoreConfiguration.class).run(ctx -> {
			HealthContributor contributor = ctx.getBean("vectorStoreHealthContributor", HealthContributor.class);
			assertThat(contributor).isInstanceOf(CompositeHealthContributor.class);
			CompositeHealthContributor composite = (CompositeHealthContributor) contributor;
			assertThat(composite.iterator()).toIterable().hasSize(2);
		});
	}

	@Test
	void healthIndicatorCanBeDisabled() {
		this.contextRunner.withUserConfiguration(SingleVectorStoreConfiguration.class)
			.withPropertyValues("management.health.vectorStore.enabled=false")
			.run(ctx -> assertThat(ctx).doesNotHaveBean("vectorStoreHealthContributor"));
	}

	@Test
	void healthIndicatorReportsDownWhenStoreThrows() {
		this.contextRunner.withUserConfiguration(FailingVectorStoreConfiguration.class).run(ctx -> {
			HealthContributor contributor = ctx.getBean("vectorStoreHealthContributor", HealthContributor.class);
			Health health = ((HealthIndicator) contributor).health();
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		});
	}

	@Test
	void noContributorRegisteredWhenNoVectorStorePresent() {
		this.contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(HealthContributor.class));
	}

	@Test
	void userDefinedContributorIsNotReplaced() {
		this.contextRunner.withUserConfiguration(SingleVectorStoreConfiguration.class)
			.withBean("vectorStoreHealthContributor", HealthContributor.class,
					() -> (HealthIndicator) () -> Health.up().withDetail("custom", true).build())
			.run(ctx -> {
				HealthContributor contributor = ctx.getBean("vectorStoreHealthContributor", HealthContributor.class);
				Health health = ((HealthIndicator) contributor).health();
				assertThat(health.getDetails()).containsKey("custom");
			});
	}

	// --- test configurations ---

	@Configuration(proxyBeanMethods = false)
	static class SingleVectorStoreConfiguration {

		@Bean
		VectorStore vectorStore() {
			return new StubVectorStore("SimpleVectorStore", false);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class MultipleVectorStoreConfiguration {

		@Bean
		VectorStore firstVectorStore() {
			return new StubVectorStore("FirstStore", false);
		}

		@Bean
		VectorStore secondVectorStore() {
			return new StubVectorStore("SecondStore", false);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class FailingVectorStoreConfiguration {

		@Bean
		VectorStore vectorStore() {
			return new StubVectorStore("FailingStore", true);
		}

	}

	static class StubVectorStore implements VectorStore {

		private final String name;

		private final boolean failOnSearch;

		StubVectorStore(String name, boolean failOnSearch) {
			this.name = name;
			this.failOnSearch = failOnSearch;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public void add(List<Document> documents) {
		}

		@Override
		public void delete(List<String> idList) {
		}

		@Override
		public void delete(Filter.Expression filterExpression) {
		}

		@Override
		public List<Document> similaritySearch(SearchRequest request) {
			if (this.failOnSearch) {
				throw new RuntimeException("Connection refused");
			}
			return List.of();
		}

		@Override
		public <T> Optional<T> getNativeClient() {
			return Optional.empty();
		}

	}

}
