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

package org.springframework.ai.model.systemone.autoconfigure;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.observation.DecisionModelObservationContext;
import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.systemone.SystemOneDecisionModel;
import org.springframework.ai.systemone.api.SystemOneClient;
import org.springframework.ai.systemone.api.SystemOneRetryUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link SystemOneDecisionAutoConfiguration},
 * {@link SystemOneConnectionProperties} and {@link SystemOneDecisionProperties}.
 *
 * @author Christian Tzolov
 */
class SystemOneDecisionAutoConfigurationTests {

	private static final String OK_BODY = """
			{"model": "jev-1.13.0", "answers": {"defect": {"type": "noul", "noul": 0.97}},
			"usage": {"input_tokens": 20, "output_tokens": 0}}""";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
		.withPropertyValues("spring.ai.systemone.api-key=test-key");

	@Test
	void createsTheClientAndTheModelWithDefaults() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(SystemOneClient.class).hasSingleBean(SystemOneDecisionModel.class);
			SystemOneDecisionModel model = context.getBean(SystemOneDecisionModel.class);
			assertThat(model.getOptions().getModel()).isEqualTo("jev-latest");
			assertThat(model.getProvider()).isEqualTo(SystemOneDecisionModel.DEFAULT_PROVIDER);
			assertThat(context.getBean(SystemOneClient.class).defaultModel()).isEqualTo("jev-latest");
			assertThat(context.getBean(SystemOneDecisionProperties.class).getModel()).isNull();
		});
	}

	@Test
	void bindsEveryProperty() {
		this.contextRunner
			.withPropertyValues("spring.ai.systemone.base-url=http://laya.test:8002", "spring.ai.systemone.timeout=45s",
					"spring.ai.systemone.retry.max-retries=4", "spring.ai.systemone.retry.initial-backoff=200ms",
					"spring.ai.systemone.retry.max-backoff=2s", "spring.ai.systemone.retry.timeout=1m",
					"spring.ai.systemone.decision.model=english", "spring.ai.systemone.decision.provider=laya")
			.run(context -> {
				SystemOneConnectionProperties connection = context.getBean(SystemOneConnectionProperties.class);
				assertThat(connection.getApiKey()).isEqualTo("test-key");
				assertThat(connection.getBaseUrl()).isEqualTo("http://laya.test:8002");
				assertThat(connection.getTimeout()).isEqualTo(Duration.ofSeconds(45));
				assertThat(connection.getRetry().toRetryPolicy()).isNotNull();
				assertThat(connection.getRetry().getMaxRetries()).isEqualTo(4);
				assertThat(connection.getRetry().getInitialBackoff()).isEqualTo(Duration.ofMillis(200));
				assertThat(connection.getRetry().getMaxBackoff()).isEqualTo(Duration.ofSeconds(2));
				assertThat(connection.getRetry().getTimeout()).isEqualTo(Duration.ofMinutes(1));
				assertThat(connection.getRetry().toRetryPolicy().getTimeout()).isEqualTo(Duration.ofMinutes(1));

				SystemOneDecisionProperties decision = context.getBean(SystemOneDecisionProperties.class);
				assertThat(decision.getModel()).isEqualTo("english");
				assertThat(decision.getProvider()).isEqualTo("laya");

				SystemOneDecisionModel model = context.getBean(SystemOneDecisionModel.class);
				assertThat(model.getOptions().getModel()).isEqualTo("english");
				assertThat(model.getProvider()).isEqualTo("laya");
				assertThat(context.getBean(SystemOneClient.class).defaultModel()).isEqualTo("english");
			});
	}

	@Test
	void theRetrySettingsKeepTheSystemOneClassification() {
		SystemOneConnectionProperties.Retry retry = new SystemOneConnectionProperties.Retry();
		retry.setMaxRetries(0);

		assertThat(retry.toRetryPolicy().shouldRetry(new IllegalStateException("not ours"))).isFalse();
		assertThat(SystemOneRetryUtils.isTransient(new IllegalStateException("not ours"))).isFalse();
	}

	@Test
	void failsFastWithoutAnApiKey() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("spring.ai.systemone.api-key");
			});
	}

	@Test
	void failsFastWithABlankApiKey() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
			.withPropertyValues("spring.ai.systemone.api-key=")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("spring.ai.systemone.api-key");
			});
	}

	@Test
	void needsNoApiKeyForADecisionModelOverAClientOfItsOwn() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
			.withUserConfiguration(OwnModelAndClientConfiguration.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(SystemOneClient.class);
				assertThat(context).hasSingleBean(SystemOneDecisionModel.class);
				assertThat(context.getBean(SystemOneDecisionModel.class).getProvider()).isEqualTo("own");
			});
	}

	@Test
	void needsNoApiKeyForAUserDefinedClient() {
		SystemOneClient own = SystemOneClient.builder().apiKey("own-key").defaultModel("own-model").build();
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SystemOneDecisionAutoConfiguration.class))
			.withBean(SystemOneClient.class, () -> own)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SystemOneClient.class)).isSameAs(own);
				assertThat(context.getBean(SystemOneDecisionModel.class).getOptions().getModel())
					.isEqualTo("own-model");
			});
	}

	@Test
	void isSkippedWhenAnotherDecisionModelIsSelected() {
		this.contextRunner.withPropertyValues("spring.ai.model.decision=ollama").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SystemOneClient.class).doesNotHaveBean(SystemOneDecisionModel.class);
		});
		this.contextRunner.withPropertyValues("spring.ai.model.decision=none")
			.run(context -> assertThat(context).doesNotHaveBean(SystemOneDecisionModel.class));
	}

	@Test
	void isSkippedWithoutTheSystemOneModule() {
		this.contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.ai.systemone")).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean("systemOneClient").doesNotHaveBean("systemOneDecisionModel");
			assertThat(context).doesNotHaveBean(SystemOneConnectionProperties.class);
		});
	}

	@Test
	void backsOffForAUserDefinedClient() {
		SystemOneClient own = SystemOneClient.builder().apiKey("own-key").defaultModel("own-model").build();
		this.contextRunner.withBean(SystemOneClient.class, () -> own).run(context -> {
			assertThat(context).hasSingleBean(SystemOneClient.class);
			assertThat(context.getBean(SystemOneClient.class)).isSameAs(own);
			assertThat(context).hasSingleBean(SystemOneDecisionModel.class);
		});
	}

	@Test
	void sendsTheDefaultModelOfAUserDefinedClientWhenNoModelIsConfigured() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(jsonPath("$.model").value("own-model"))
			.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
		SystemOneClient own = SystemOneClient.builder()
			.apiKey("own-key")
			.defaultModel("own-model")
			.restClientBuilder(restClientBuilder)
			.build();

		this.contextRunner.withBean(SystemOneClient.class, () -> own).run(context -> {
			SystemOneDecisionModel model = context.getBean(SystemOneDecisionModel.class);
			assertThat(model.getOptions().getModel()).isEqualTo("own-model");

			model.call("My new headphones arrived yesterday, but the left side doesn't work.",
					Map.of("defect", YesNoQuestion.of("Does the customer report a defect?")));
			server.verify();
		});
	}

	@Test
	void sendsTheConfiguredModelOverTheDefaultModelOfAUserDefinedClient() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(jsonPath("$.model").value("configured-model"))
			.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
		SystemOneClient own = SystemOneClient.builder()
			.apiKey("own-key")
			.defaultModel("own-model")
			.restClientBuilder(restClientBuilder)
			.build();

		this.contextRunner.withPropertyValues("spring.ai.systemone.decision.model=configured-model")
			.withBean(SystemOneClient.class, () -> own)
			.run(context -> {
				SystemOneDecisionModel model = context.getBean(SystemOneDecisionModel.class);
				assertThat(model.getOptions().getModel()).isEqualTo("configured-model");
				assertThat(context.getBean(SystemOneClient.class).defaultModel()).isEqualTo("own-model");

				model.call("My new headphones arrived yesterday, but the left side doesn't work.",
						Map.of("defect", YesNoQuestion.of("Does the customer report a defect?")));
				server.verify();
			});
	}

	@Test
	void treatsABlankModelAsUnset() {
		this.contextRunner.withPropertyValues("spring.ai.systemone.decision.model=").run(context -> {
			assertThat(context.getBean(SystemOneDecisionModel.class).getOptions().getModel()).isEqualTo("jev-latest");
			assertThat(context.getBean(SystemOneClient.class).defaultModel()).isEqualTo("jev-latest");
		});
	}

	@Test
	void backsOffForAUserDefinedDecisionModel() {
		this.contextRunner.withUserConfiguration(OwnModelConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(SystemOneDecisionModel.class);
			assertThat(context.getBean(SystemOneDecisionModel.class).getProvider()).isEqualTo("own");
		});
	}

	@Test
	void sendsDecisionsToTheConfiguredServerThroughTheApplicationRestClientBuilder() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(requestTo("http://systemone.test:8002/v1/systemone"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
			.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		this.contextRunner.withPropertyValues("spring.ai.systemone.base-url=http://systemone.test:8002")
			.withBean(RestClient.Builder.class, () -> restClientBuilder)
			.run(context -> {
				DecisionResponse response = context.getBean(SystemOneDecisionModel.class)
					.call("My new headphones arrived yesterday, but the left side doesn't work.",
							Map.of("defect", YesNoQuestion.of("Does the customer report a defect?")));

				assertThat(response.getYesNo("defect").isYes()).isTrue();
				server.verify();
			});
	}

	@Test
	void appliesTheReadTimeoutOnTopOfTheApplicationHttpClientSettings() {
		AtomicReference<@Nullable HttpClientSettings> used = new AtomicReference<>();
		ClientHttpRequestFactoryBuilder<JdkClientHttpRequestFactory> factoryBuilder = settings -> {
			used.set(settings);
			return new JdkClientHttpRequestFactory();
		};
		HttpClientSettings applicationSettings = HttpClientSettings.defaults()
			.withConnectTimeout(Duration.ofSeconds(3));

		this.contextRunner.withPropertyValues("spring.ai.systemone.timeout=7s")
			.withBean(RestClient.Builder.class, RestClient::builder)
			.withBean(ClientHttpRequestFactoryBuilder.class, () -> factoryBuilder)
			.withBean(HttpClientSettings.class, () -> applicationSettings)
			.run(context -> {
				assertThat(context).hasSingleBean(SystemOneClient.class);
				HttpClientSettings settings = used.get();
				assertThat(settings).isNotNull();
				assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(7));
				assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
			});
	}

	@Test
	void leavesTheApplicationTransportAloneWhenNoTimeoutIsSet() {
		AtomicReference<@Nullable HttpClientSettings> used = new AtomicReference<>();
		ClientHttpRequestFactoryBuilder<JdkClientHttpRequestFactory> factoryBuilder = settings -> {
			used.set(settings);
			return new JdkClientHttpRequestFactory();
		};

		this.contextRunner.withBean(RestClient.Builder.class, RestClient::builder)
			.withBean(ClientHttpRequestFactoryBuilder.class, () -> factoryBuilder)
			.run(context -> {
				assertThat(context).hasSingleBean(SystemOneClient.class);
				assertThat(used.get()).isNull();
			});
	}

	@Test
	void appliesTheReadTimeoutWithADetectedFactoryBuilderWhenTheApplicationHasNone() {
		this.contextRunner.withPropertyValues("spring.ai.systemone.timeout=7s")
			.withBean(RestClient.Builder.class, RestClient::builder)
			.run(context -> assertThat(context).hasSingleBean(SystemOneClient.class));
	}

	@Test
	void rejectsRetrySettingsThatCannotWorkNamingThePrefix() {
		SystemOneConnectionProperties.Retry retry = new SystemOneConnectionProperties.Retry();
		retry.setMaxBackoff(Duration.ZERO);
		assertThatIllegalStateException().isThrownBy(retry::toRetryPolicy)
			.withMessageContaining("spring.ai.systemone.retry")
			.withMessageContaining("maxBackoff");

		retry.setMaxBackoff(Duration.ofSeconds(1));
		retry.setInitialBackoff(Duration.ofSeconds(10));
		assertThatIllegalStateException().isThrownBy(retry::toRetryPolicy)
			.withMessageContaining("spring.ai.systemone.retry")
			.withMessageContaining("maxBackoff")
			.withMessageContaining("initialBackoff");

		this.contextRunner.withPropertyValues("spring.ai.systemone.retry.timeout=0s").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.hasMessageContaining("spring.ai.systemone.retry")
				.hasMessageContaining("timeout");
		});
	}

	@Test
	void customObservationConventionIsApplied() {
		this.contextRunner.withUserConfiguration(CustomConventionConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(SystemOneDecisionModel.class)
				.hasSingleBean(DecisionModelObservationConvention.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class OwnModelConfiguration {

		@Bean
		SystemOneDecisionModel ownDecisionModel(SystemOneClient systemOneClient) {
			return SystemOneDecisionModel.builder(systemOneClient).provider("own").build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class OwnModelAndClientConfiguration {

		@Bean
		SystemOneDecisionModel ownDecisionModel() {
			SystemOneClient client = SystemOneClient.builder().apiKey("own-key").defaultModel("own-model").build();
			return SystemOneDecisionModel.builder(client).provider("own").build();
		}

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
