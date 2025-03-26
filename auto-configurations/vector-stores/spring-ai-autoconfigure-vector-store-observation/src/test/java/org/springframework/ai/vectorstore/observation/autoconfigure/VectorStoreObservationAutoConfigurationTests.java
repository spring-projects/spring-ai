/*
 * Copyright 2023-2024 the original author or authors.
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

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationFilter;
import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VectorStoreObservationAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
class VectorStoreObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VectorStoreObservationAutoConfiguration.class));

	@Test
	void queryResponseFilterDefault() {
		this.contextRunner
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationFilter.class));
	}

	@Test
	void queryResponseHandlerDefault() {
		this.contextRunner
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class));
	}

	@Test
	void queryResponseHandlerEnabled() {
		this.contextRunner
			.withBean(OtelTracer.class, OpenTelemetry.noop().getTracer("test"), new OtelCurrentTraceContext(), null)
			.withPropertyValues("spring.ai.vectorstore.observations.include-query-response=true")
			.run(context -> assertThat(context).hasSingleBean(VectorStoreQueryResponseObservationHandler.class));
	}

}
