/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.client.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class SseWebFluxTransportAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SseWebFluxTransportAutoConfiguration.class));

	@Test
	void webFluxClientTransportsPresentIfWebFluxSseClientTransportPresent() {

		this.applicationContext.run((context) -> {
			assertThat(context.containsBean("webFluxClientTransports")).isTrue();
		});
	}

	@Test
	void webFluxClientTransportsNotPresentIfMissingWebFluxSseClientTransportNotPresent() {

		this.applicationContext
			.withClassLoader(
					new FilteredClassLoader("io.modelcontextprotocol.client.transport.WebFluxSseClientTransport"))
			.run((context) -> {
				assertThat(context.containsBean("webFluxClientTransports")).isFalse();
			});
	}

	@Test
	void webFluxClientTransportsNotPresentIfMcpClientDisabled() {

		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled", "false").run((context) -> {
			assertThat(context.containsBean("webFluxClientTransports")).isFalse();
		});
	}

}
