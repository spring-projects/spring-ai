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

package org.springframework.ai.vllm.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vllm.VllmScoringModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Tests for {@link VllmScoringAutoConfiguration}.
 *
 * @author Spring AI
 */
class VllmScoringAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.vllm.scoring.options.model=BAAI/bge-reranker-v2-m3")
		.withConfiguration(AutoConfigurations.of(VllmScoringAutoConfiguration.class));

	@Test
	void propertiesAreLoaded() {
		this.contextRunner.run(context -> {
			VllmScoringProperties properties = context.getBean(VllmScoringProperties.class);
			assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8000");
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getOptions().getModel()).isEqualTo("BAAI/bge-reranker-v2-m3");
		});
	}

	@Test
	void beansAreCreated() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(VllmScoringModel.class));
	}

	@Test
	void configurationCanBeDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.vllm.scoring.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(VllmScoringModel.class));
	}

}
