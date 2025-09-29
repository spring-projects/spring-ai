/*
 * Copyright 2023-2025 the original author or authors.
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

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link VectorStoreObservationAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class VectorStoreObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VectorStoreObservationAutoConfiguration.class));

	@Test
	void queryResponseHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void queryResponseHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void queryResponseHandlerEnabledNoTracer(CapturedOutput output) {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=true")
			.run(context -> assertThat(context).hasSingleBean(VectorStoreQueryResponseObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out of the query response content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void queryResponseHandlerEnabledWithTracer(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=true")
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
				.hasSingleBean(TracingAwareLoggingObservationHandler.class));
		assertThat(output).contains(
				"You have enabled logging out of the query response content with the risk of exposing sensitive or private information. Please, be careful!");
	}

	@Test
	void queryResponseHandlerDisabledNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=false")
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void queryResponseHandlerDisabledWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=false")
			.run(context -> assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customQueryResponseHandlerNoTracer() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withUserConfiguration(CustomVectorStoreQueryResponseObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=true")
			.run(context -> assertThat(context).hasSingleBean(VectorStoreQueryResponseObservationHandler.class)
				.hasBean("customVectorStoreQueryResponseObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customQueryResponseHandlerWithTracer() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomVectorStoreQueryResponseObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=true")
			.run(context -> assertThat(context).hasSingleBean(VectorStoreQueryResponseObservationHandler.class)
				.hasBean("customVectorStoreQueryResponseObservationHandler")
				.doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
	}

	@Test
	void customTracingAwareLoggingObservationHandler() {
		this.contextRunner.withUserConfiguration(TracerConfiguration.class)
			.withUserConfiguration(CustomTracingAwareLoggingObservationHandlerConfiguration.class)
			.withPropertyValues("spring.ai.vectorstore.observations.log-query-response=true")
			.run(context -> {
				assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationHandler.class)
					.hasSingleBean(TracingAwareLoggingObservationHandler.class)
					.hasBean("vectorStoreQueryResponseObservationHandler");
				assertThat(context.getBean(TracingAwareLoggingObservationHandler.class))
					.isSameAs(CustomTracingAwareLoggingObservationHandlerConfiguration.handlerInstance);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class TracerConfiguration {

		@Bean
		Tracer tracer() {
			return mock(Tracer.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomVectorStoreQueryResponseObservationHandlerConfiguration {

		@Bean
		VectorStoreQueryResponseObservationHandler customVectorStoreQueryResponseObservationHandler() {
			return new VectorStoreQueryResponseObservationHandler();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTracingAwareLoggingObservationHandlerConfiguration {

		static TracingAwareLoggingObservationHandler<VectorStoreObservationContext> handlerInstance = new TracingAwareLoggingObservationHandler<>(
				new VectorStoreQueryResponseObservationHandler(), null);

		@Bean
		TracingAwareLoggingObservationHandler<VectorStoreObservationContext> vectorStoreQueryResponseObservationHandler() {
			return handlerInstance;
		}

	}

}
