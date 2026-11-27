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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link McpStdioClientProperties}.
 */
class McpStdioClientPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void stdioConnectionWithoutArgsUsesNoArguments() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=echo")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);

				assertThatCode(properties::toServerParameters).doesNotThrowAnyException();
				assertThat(properties.toServerParameters()).containsKey("server1");
			});
	}

	@Test
	void stdioConnectionIsLegacyUnlessItSetsTheModernProtocol() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.legacy.command=echo",
					"spring.ai.mcp.client.stdio.connections.modern.command=echo",
					"spring.ai.mcp.client.stdio.connections.modern.args[0]=hello",
					"spring.ai.mcp.client.stdio.connections.modern.protocol=MODERN",
					"spring.ai.mcp.client.stdio.connections.modern.client-scope=SHARED",
					"spring.ai.mcp.client.stdio.connections.modern.listen=true")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);

				Parameters legacy = properties.getConnections().get("legacy");
				assertThat(legacy.isModern()).isFalse();
				assertThat(legacy.isSharedClient()).isFalse();
				assertThat(legacy.isCustomClientScope()).isFalse();
				assertThat(legacy.isListening()).isFalse();

				Parameters modern = properties.getConnections().get("modern");
				assertThat(modern.isModern()).isTrue();
				assertThat(modern.isSharedClient()).isTrue();
				assertThat(modern.isCustomClientScope()).isFalse();
				assertThat(modern.isListening()).isTrue();
				assertThat(modern.toServerParameters().getArgs()).containsExactly("hello");
			});
	}

	@Test
	void serversConfigurationResourceMayCarryTheProtocolOfAConnection() {
		McpStdioClientProperties properties = new McpStdioClientProperties();
		properties.setServersConfiguration(new ByteArrayResource("""
				{ "mcpServers": {
				"files": { "command": "files-server", "args": ["--root", "/tmp"] },
				"notes": { "command": "notes-server", "protocol": "MODERN", "clientScope": "CUSTOM", "listen": true }
				} }
				""".getBytes(StandardCharsets.UTF_8)));

		Map<String, Parameters> connections = properties.allConnections();

		assertThat(connections).containsOnlyKeys("files", "notes");
		assertThat(connections.get("files").isModern()).isFalse();
		assertThat(connections.get("files").toServerParameters().getArgs()).containsExactly("--root", "/tmp");
		assertThat(connections.get("notes").isModern()).isTrue();
		assertThat(connections.get("notes").isCustomClientScope()).isTrue();
		assertThat(connections.get("notes").isListening()).isTrue();
		assertThat(properties.toServerParameters()).containsOnlyKeys("files", "notes");
	}

	@Test
	void connectionOfThePropertiesReplacesTheOneOfTheResourceWithTheSameName() {
		McpStdioClientProperties properties = new McpStdioClientProperties();
		properties.setServersConfiguration(new ByteArrayResource("""
				{ "mcpServers": { "files": { "command": "files-server" } } }
				""".getBytes(StandardCharsets.UTF_8)));
		properties.getConnections()
			.put("files",
					new Parameters("other-server", null, null, McpClientCommonProperties.Protocol.MODERN, null, null));

		assertThat(properties.allConnections().get("files").command()).isEqualTo("other-server");
		assertThat(properties.allConnections().get("files").isModern()).isTrue();
	}

	@Test
	void parametersAreWrittenToJsonWithTheirComponentsOnly() {
		JsonMapper jsonMapper = JsonMapper.shared();

		// As before the modern settings existed, for a connection without them.
		String legacy = jsonMapper.writeValueAsString(new Parameters("echo", List.of("hello"), null));
		assertThat(legacy).isEqualTo("{\"command\":\"echo\",\"args\":[\"hello\"]}");

		Parameters modern = new Parameters("echo", null, null, McpClientCommonProperties.Protocol.MODERN,
				McpClientModernProperties.ClientScope.SHARED, true);
		String json = jsonMapper.writeValueAsString(modern);
		assertThat(json)
			.isEqualTo("{\"command\":\"echo\",\"protocol\":\"MODERN\",\"clientScope\":\"SHARED\",\"listen\":true}");
		assertThat(jsonMapper.readValue(json, Parameters.class)).isEqualTo(modern);
	}

	@Test
	void serversConfigurationResourceIsReadOnce() {
		AtomicInteger reads = new AtomicInteger();
		McpStdioClientProperties properties = new McpStdioClientProperties();
		properties.setServersConfiguration(new ByteArrayResource("""
				{ "mcpServers": { "files": { "command": "files-server" } } }
				""".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public InputStream getInputStream() throws IOException {
				reads.incrementAndGet();
				return super.getInputStream();
			}
		});

		properties.allConnections();
		properties.allConnections();
		properties.toServerParameters();

		assertThat(reads).hasValue(1);
	}

	@Test
	void parametersOfThreeArgumentsAreLegacy() {
		Parameters parameters = new Parameters("echo", List.of("hello"), Map.of("KEY", "value"));

		assertThat(parameters.isModern()).isFalse();
		assertThat(parameters.protocol()).isNull();
		assertThat(parameters.clientScope()).isNull();
		assertThat(parameters.listen()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(McpStdioClientProperties.class)
	static class TestConfiguration {

	}

}
