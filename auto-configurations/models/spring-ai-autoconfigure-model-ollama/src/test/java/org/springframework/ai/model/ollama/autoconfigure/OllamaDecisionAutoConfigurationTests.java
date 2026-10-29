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

package org.springframework.ai.model.ollama.autoconfigure;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.observation.DecisionModelObservationContext;
import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaDecisionModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link OllamaDecisionAutoConfiguration} and
 * {@link OllamaDecisionProperties}.
 *
 * @author Thomas Vitale
 */
class OllamaDecisionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaDecisionAutoConfiguration.class));

	@Test
	void decisionModelIsCreatedWithDefaultModel() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(OllamaDecisionModel.class);
			assertThat(context.getBean(OllamaDecisionModel.class).getOptions().getModel())
				.isEqualTo(OllamaDecisionModel.DEFAULT_MODEL);
		});
	}

	@Test
	void decisionProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.ollama.base-url=TEST_BASE_URL", "spring.ai.ollama.decision.model=MODEL_XYZ",
					"spring.ai.ollama.init.decision.include=false",
					"spring.ai.ollama.init.decision.additional-models=d1-omni:2b")
			.run(context -> {
				var decisionProperties = context.getBean(OllamaDecisionProperties.class);
				var connectionProperties = context.getBean(OllamaConnectionProperties.class);
				var initProperties = context.getBean(OllamaInitializationProperties.class);

				assertThat(decisionProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(initProperties.getDecision().isInclude()).isFalse();
				assertThat(initProperties.getDecision().getAdditionalModels()).containsExactly("d1-omni:2b");
				assertThat(context.getBean(OllamaDecisionModel.class).getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	void decisionsAreSentToTheConfiguredOllamaServer() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(requestTo("http://ollama.test:11434/v1/systemone"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ollama"))
			.andRespond(withSuccess("""
					{"model": "d1-omni:600m", "answers": {"mimic": {"type": "noul", "noul": 0.97}},
					"usage": {"input_tokens": 20, "output_tokens": 0}}""", MediaType.APPLICATION_JSON));

		this.contextRunner.withPropertyValues("spring.ai.ollama.base-url=http://ollama.test:11434")
			.withBean(RestClient.Builder.class, () -> restClientBuilder)
			.run(context -> {
				DecisionResponse response = context.getBean(OllamaDecisionModel.class)
					.call("The treasure chest just licked its lips.",
							Map.of("mimic", YesNoQuestion.of("Is the chest a mimic?")));

				assertThat(response.getYesNo("mimic").isYes()).isTrue();
				server.verify();
			});
	}

	@Test
	void decisionModelIsSkippedWhenTheTypeSafeSdkIsExcluded() {
		new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("org.springaicommunity.typesafe"))
			.withConfiguration(BaseOllamaIT.ollamaAutoConfig(OllamaChatAutoConfiguration.class,
					OllamaEmbeddingAutoConfiguration.class, OllamaDecisionAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(OllamaChatModel.class);
				assertThat(context).hasSingleBean(OllamaEmbeddingModel.class);
				assertThat(context).doesNotHaveBean(OllamaDecisionModel.class);
				assertThat(context).doesNotHaveBean(OllamaDecisionProperties.class);
			});
	}

	@Test
	void customObservationConventionIsApplied() {
		this.contextRunner.withUserConfiguration(CustomConventionConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(OllamaDecisionModel.class)
				.hasSingleBean(DecisionModelObservationConvention.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConventionConfiguration {

		@Bean
		DecisionModelObservationConvention customDecisionModelObservationConvention() {
			return new DecisionModelObservationConvention() {
				@Override
				public String getName() {
					return "custom";
				}

				@Override
				public String getContextualName(DecisionModelObservationContext context) {
					return "custom";
				}
			};
		}

	}

}
