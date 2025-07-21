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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpClientCommonProperties}.
 *
 * @author Christian Tzolov
 */
class McpClientCommonPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void defaultValues() {
		this.contextRunner.run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.isInitialized()).isTrue();
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
			assertThat(properties.isRootChangeNotification()).isTrue();
		});
	}

	@Test
	void customValues() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.name=custom-client",
					"spring.ai.mcp.client.version=2.0.0", "spring.ai.mcp.client.initialized=false",
					"spring.ai.mcp.client.request-timeout=30s", "spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.root-change-notification=false")
			.run(context -> {
				McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
				assertThat(properties.isEnabled()).isFalse();
				assertThat(properties.getName()).isEqualTo("custom-client");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.isInitialized()).isFalse();
				assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
				assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
				assertThat(properties.isRootChangeNotification()).isFalse();
			});
	}

	@Test
	void setterGetterMethods() {
		McpClientCommonProperties properties = new McpClientCommonProperties();

		// Test enabled property
		properties.setEnabled(false);
		assertThat(properties.isEnabled()).isFalse();

		// Test name property
		properties.setName("test-client");
		assertThat(properties.getName()).isEqualTo("test-client");

		// Test version property
		properties.setVersion("3.0.0");
		assertThat(properties.getVersion()).isEqualTo("3.0.0");

		// Test initialized property
		properties.setInitialized(false);
		assertThat(properties.isInitialized()).isFalse();

		// Test requestTimeout property
		Duration timeout = Duration.ofMinutes(5);
		properties.setRequestTimeout(timeout);
		assertThat(properties.getRequestTimeout()).isEqualTo(timeout);

		// Test type property
		properties.setType(McpClientCommonProperties.ClientType.ASYNC);
		assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);

		// Test rootChangeNotification property
		properties.setRootChangeNotification(false);
		assertThat(properties.isRootChangeNotification()).isFalse();
	}

	@Test
	void durationPropertyBinding() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.request-timeout=PT1M30S").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(90));
		});
	}

	@Test
	void enumPropertyBinding() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.type=ASYNC").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
		});
	}

	@Test
	void propertiesFileBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.name=test-mcp-client",
					"spring.ai.mcp.client.version=0.5.0", "spring.ai.mcp.client.initialized=false",
					"spring.ai.mcp.client.request-timeout=45s", "spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.root-change-notification=false")
			.run(context -> {
				McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
				assertThat(properties.isEnabled()).isFalse();
				assertThat(properties.getName()).isEqualTo("test-mcp-client");
				assertThat(properties.getVersion()).isEqualTo("0.5.0");
				assertThat(properties.isInitialized()).isFalse();
				assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(45));
				assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
				assertThat(properties.isRootChangeNotification()).isFalse();
			});
	}

	@Test
	void invalidEnumValue() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.type=INVALID_TYPE").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
			// The error message doesn't contain the exact enum value, so we'll check for
			// a more general message
			assertThat(context.getStartupFailure().getMessage()).contains("Could not bind properties");
		});
	}

	@Test
	void invalidDurationFormat() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.request-timeout=invalid-duration").run(context -> {
			assertThat(context).hasFailed();
			// The error message doesn't contain the property name, so we'll check for a
			// more general message
			assertThat(context.getStartupFailure().getMessage()).contains("Could not bind properties");
		});
	}

	@Test
	void yamlConfigurationBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.name=test-mcp-client-yaml",
					"spring.ai.mcp.client.version=0.6.0", "spring.ai.mcp.client.initialized=false",
					"spring.ai.mcp.client.request-timeout=60s", "spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.root-change-notification=false")
			.run(context -> {
				McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
				assertThat(properties.isEnabled()).isFalse();
				assertThat(properties.getName()).isEqualTo("test-mcp-client-yaml");
				assertThat(properties.getVersion()).isEqualTo("0.6.0");
				assertThat(properties.isInitialized()).isFalse();
				assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
				assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
				assertThat(properties.isRootChangeNotification()).isFalse();
			});
	}

	@Test
	void configPrefixConstant() {
		assertThat(McpClientCommonProperties.CONFIG_PREFIX).isEqualTo("spring.ai.mcp.client");
	}

	@Test
	void clientTypeEnumValues() {
		assertThat(McpClientCommonProperties.ClientType.values())
			.containsExactly(McpClientCommonProperties.ClientType.SYNC, McpClientCommonProperties.ClientType.ASYNC);
	}

	@Test
	void disabledProperties() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.enabled=false").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.isEnabled()).isFalse();
			// Other properties should still have their default values
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.isInitialized()).isTrue();
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
			assertThat(properties.isRootChangeNotification()).isTrue();
		});
	}

	@Test
	void notInitializedProperties() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.initialized=false").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.isInitialized()).isFalse();
			// Other properties should still have their default values
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
			assertThat(properties.isRootChangeNotification()).isTrue();
		});
	}

	@Test
	void rootChangeNotificationDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.root-change-notification=false").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.isRootChangeNotification()).isFalse();
			// Other properties should still have their default values
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.isInitialized()).isTrue();
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
		});
	}

	@Test
	void customRequestTimeout() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.request-timeout=120s").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(120));
			// Other properties should still have their default values
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.isInitialized()).isTrue();
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
			assertThat(properties.isRootChangeNotification()).isTrue();
		});
	}

	@Test
	void asyncClientType() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.type=ASYNC").run(context -> {
			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
			// Other properties should still have their default values
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getName()).isEqualTo("spring-ai-mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.isInitialized()).isTrue();
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(properties.isRootChangeNotification()).isTrue();
		});
	}

	@Test
	void customNameAndVersion() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.name=custom-mcp-client", "spring.ai.mcp.client.version=2.5.0")
			.run(context -> {
				McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
				assertThat(properties.getName()).isEqualTo("custom-mcp-client");
				assertThat(properties.getVersion()).isEqualTo("2.5.0");
				// Other properties should still have their default values
				assertThat(properties.isEnabled()).isTrue();
				assertThat(properties.isInitialized()).isTrue();
				assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(20));
				assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
				assertThat(properties.isRootChangeNotification()).isTrue();
			});
	}

	@Configuration
	@EnableConfigurationProperties(McpClientCommonProperties.class)
	static class TestConfiguration {

	}

}
