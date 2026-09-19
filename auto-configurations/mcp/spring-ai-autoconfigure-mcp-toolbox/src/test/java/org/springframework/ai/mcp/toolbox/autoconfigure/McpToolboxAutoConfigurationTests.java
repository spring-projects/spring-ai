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

package org.springframework.ai.mcp.toolbox.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.cloud.mcp.McpToolboxClient;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.toolbox.McpToolboxToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Auto-configuration tests for {@link McpToolboxAutoConfiguration}.
 *
 * @author Stenal P Jolly
 */
class McpToolboxAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolboxAutoConfiguration.class));

	@Test
	void shouldRegisterBeansByDefault() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.toolbox.url=http://localhost:5005/mcp",
					"spring.ai.mcp.toolbox.client-name=custom-client", "spring.ai.mcp.toolbox.client-version=2.0.0",
					"spring.ai.mcp.toolbox.timeout=15s")
			.run(context -> {
				assertThat(context).hasSingleBean(McpToolboxClient.class);
				assertThat(context).hasSingleBean(McpToolboxToolCallbackProvider.class);
				McpToolboxProperties props = context.getBean(McpToolboxProperties.class);
				assertThat(props.getUrl()).isEqualTo("http://localhost:5005/mcp");
				assertThat(props.getClientName()).isEqualTo("custom-client");
				assertThat(props.getClientVersion()).isEqualTo("2.0.0");
				assertThat(props.getTimeout().getSeconds()).isEqualTo(15);
			});
	}

	@Test
	void shouldNotRegisterBeansWhenDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.toolbox.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(McpToolboxClient.class);
			assertThat(context).doesNotHaveBean(McpToolboxToolCallbackProvider.class);
		});
	}

	@Test
	void shouldBackOffWhenCustomMcpToolboxClientBeanIsProvided() {
		McpToolboxClient customClient = mock(McpToolboxClient.class);
		this.contextRunner.withBean(McpToolboxClient.class, () -> customClient).run(context -> {
			assertThat(context).hasSingleBean(McpToolboxClient.class);
			assertThat(context.getBean(McpToolboxClient.class)).isSameAs(customClient);
			assertThat(context).hasSingleBean(McpToolboxToolCallbackProvider.class);
		});
	}

	@Test
	void shouldInvokeMcpToolboxClientCustomizer() {
		AtomicBoolean customized = new AtomicBoolean(false);
		McpToolboxClientCustomizer customizer = builder -> customized.set(true);

		this.contextRunner.withBean(McpToolboxClientCustomizer.class, () -> customizer).run(context -> {
			assertThat(context).hasSingleBean(McpToolboxClient.class);
			assertThat(customized.get()).isTrue();
		});
	}

	@Test
	void propertiesShouldEnforceDefensiveCopyingMaskSecretsAndValidateUrl() {
		McpToolboxProperties props = new McpToolboxProperties();
		props.setApiKey("super-secret-api-key");

		Map<String, String> mutableHeaders = new HashMap<>();
		mutableHeaders.put("Authorization", "Bearer token-123");
		props.setHeaders(mutableHeaders);
		mutableHeaders.put("Injected", "evil");

		assertThat(props.getHeaders()).doesNotContainKey("Injected");
		assertThatThrownBy(() -> props.getHeaders().put("Mutate", "fail"))
			.isInstanceOf(UnsupportedOperationException.class);

		String toStringOutput = props.toString();
		assertThat(toStringOutput).doesNotContain("super-secret-api-key");
		assertThat(toStringOutput).doesNotContain("Bearer token-123");
		assertThat(toStringOutput).contains("apiKey='***'");
		assertThat(toStringOutput).contains("headers=[REDACTED]");

		assertThatThrownBy(() -> props.setUrl("ftp://invalid-scheme.local"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("http:// or https://");
	}

}
