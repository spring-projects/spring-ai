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

package org.springframework.ai.openai.chat;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests proving HTTP-layer observability is restored after the OpenAi SDK
 * migration. Each outbound HTTP attempt to the OpenAi API must:
 * <ul>
 * <li>emit an {@code okhttp.requests} observation,</li>
 * <li>record a timer in the {@link MeterRegistry},</li>
 * <li>register OkHttp connection-pool gauges.</li>
 * </ul>
 * For synchronous calls the HTTP observation is also parented under the
 * {@code gen_ai.client.operation} chat-model observation. The streaming case verifies the
 * wiring still fires but does not assert the parent linkage — see the comment on
 * {@link #httpObservationFiresAndMetricsRecordedForStreamingCall()} for the SDK
 * limitation behind this.
 *
 * @author Soby Chacko
 */
@SpringBootTest(classes = OpenAiChatModelHttpObservabilityIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatModelHttpObservabilityIT {

	private static final String TEST_MODEL = "gpt-4o-mini";

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	OpenAiChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void httpObservationFiresAndIsParentedUnderChatObservationForSyncCall() {
		var options = OpenAiChatOptions.builder().model(TEST_MODEL).maxTokens(128).build();

		ChatResponse response = this.chatModel.call(new Prompt("Say hi in one word.", options));
		assertThat(response.getResult().getOutput().getText()).isNotEmpty();

		assertHttpObservationParentedUnderChatModel();
		assertHttpTimerRecorded();
		assertConnectionPoolGaugesBound();
	}

	/**
	 * Asserts streaming wiring (observation, timer, pool gauges) but deliberately not the
	 * parent-child linkage: the SDK's async path uses
	 * {@code CompletableFuture.thenComposeAsync(...)} without an explicit executor,
	 * jumping to {@code ForkJoinPool.commonPool()} before our HTTP client runs. The
	 * calling thread's parent observation is gone by then, so the HTTP span is recorded
	 * but unparented.
	 */
	@Test
	void httpObservationFiresAndMetricsRecordedForStreamingCall() {
		var options = OpenAiChatOptions.builder().model(TEST_MODEL).maxTokens(128).build();

		Flux<ChatResponse> flux = this.chatModel.stream(new Prompt("Say hi in one word.", options));
		List<ChatResponse> responses = flux.collectList().block();
		assertThat(responses).isNotEmpty();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME);
		assertHttpTimerRecorded();
		assertConnectionPoolGaugesBound();
	}

	private void assertHttpObservationParentedUnderChatModel() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(
					parent -> DefaultChatModelObservationConvention.DEFAULT_NAME.equals(parent.getName()),
					"parent observation '%s'".formatted(DefaultChatModelObservationConvention.DEFAULT_NAME));
	}

	private void assertHttpTimerRecorded() {
		Timer httpTimer = this.meterRegistry.find(HTTP_OBSERVATION_NAME).timer();
		assertThat(httpTimer).as("Micrometer should record an %s timer", HTTP_OBSERVATION_NAME).isNotNull();
		assertThat(httpTimer.count()).isGreaterThanOrEqualTo(1L);
	}

	private void assertConnectionPoolGaugesBound() {
		long poolMeters = this.meterRegistry.getMeters()
			.stream()
			.map(m -> m.getId().getName())
			.filter(n -> n.startsWith("okhttp.pool"))
			.count();
		assertThat(poolMeters).as("OkHttpConnectionPoolMetrics should register at least one okhttp.pool.* gauge")
			.isGreaterThan(0);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		public TestObservationRegistry observationRegistry(MeterRegistry meterRegistry) {
			TestObservationRegistry registry = TestObservationRegistry.create();
			// Bridge observations to meters so the OkHttp interceptor's Observation
			// becomes a Timer entry in the SimpleMeterRegistry.
			registry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
			return registry;
		}

		@Bean
		public OpenAiChatModel openaiSdkChatModel(TestObservationRegistry observationRegistry,
				MeterRegistry meterRegistry) {
			return OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder().build())
				.observationRegistry(observationRegistry)
				.meterRegistry(meterRegistry)
				.build();
		}

	}

}
