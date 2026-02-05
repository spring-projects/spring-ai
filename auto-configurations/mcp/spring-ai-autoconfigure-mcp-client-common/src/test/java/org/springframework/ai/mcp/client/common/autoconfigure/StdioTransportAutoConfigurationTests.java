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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.lang.reflect.Field;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StdioTransportAutoConfiguration}.
 *
 * @author guan xu
 */
@SuppressWarnings("unchecked")
public class StdioTransportAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(StdioTransportAutoConfiguration.class));

	@Test
	void stdioTransportsNotPresentIfStdioDisabled() {
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled", "false")
			.run(context -> assertThat(context.containsBean("stdioTransports")).isFalse());
	}

	@Test
	void noTransportsCreatedWithEmptyConnections() {
		this.applicationContext.run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
			assertThat(transports).isEmpty();
		});
	}

	@Test
	void singleConnectionCreateOneTransport() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(StdioClientTransport.class);
			});
	}

	@Test
	void multipleConnectionsCreateMultipleTransports() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server2.command=python",
					"spring.ai.mcp.client.stdio.connections.server2.args[0]=server2.py")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof StdioClientTransport);
			});
	}

	@Test
	void serversConfigurationCreateMultipleTransports() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.stdio.serversConfiguration=classpath:test-mcp-servers.json")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof StdioClientTransport);
			});
	}

	@Test
	void customObjectMapperIsUsed() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123")
			.withUserConfiguration(CustomObjectMapperConfiguration.class)
			.run(context -> {
				assertThat(context.getBean(ObjectMapper.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(StdioClientTransport.class);
				Field privateField = ReflectionUtils.findField(StdioClientTransport.class, "jsonMapper");
				ReflectionUtils.makeAccessible(privateField);
				assertThat(privateField.get(transports.get(0).transport())).isNotNull();
			});
	}

	@Test
	void newObjectMapperIsUsed() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123")
			.run(context -> {
				assertThatThrownBy(() -> context.getBean(ObjectMapper.class))
					.isInstanceOf(NoSuchBeanDefinitionException.class);
				List<NamedClientMcpTransport> transports = context.getBean("stdioTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(StdioClientTransport.class);
				Field privateField = ReflectionUtils.findField(StdioClientTransport.class, "jsonMapper");
				ReflectionUtils.makeAccessible(privateField);
				assertThat(privateField.get(transports.get(0).transport())).isNotNull();
			});
	}

	@Configuration
	static class CustomObjectMapperConfiguration {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

	}

}
