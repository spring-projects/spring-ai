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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests for the modern STDIO connections of {@link McpClientModernAutoConfiguration}.
 * They start {@link ModernStdioTestServer} as a server process.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class McpClientModernStdioAutoConfigurationTests {

	private static final String FILES = "spring.ai.mcp.client.stdio.connections.files.";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(StdioTransportAutoConfiguration.class, McpClientAutoConfiguration.class,
					McpToolCallbackAutoConfiguration.class, McpClientModernAutoConfiguration.class));

	@Test
	void aModernStdioConnectionListsAndCallsTheToolsOfItsServerProcess() {
		this.contextRunner.withPropertyValues(testServer("files", "protocol=MODERN")).run(context -> {
			assertThat(context).hasNotFailed();

			// The connection is modern only: no legacy transport is created for it.
			assertThat(named(context.getBean("stdioTransports"))).isEmpty();
			assertThat(named(context.getBean("modernStdioConnectionTransports"))).hasSize(1);

			ToolCallback[] tools = context.getBean(ToolCallbackProvider.class).getToolCallbacks();

			assertThat(names(tools)).containsExactlyInAnyOrder("files_echo", "files_add_tool");
			assertThat(callback(tools, "files_echo").call("{\"text\":\"hello\"}")).contains("echo: hello");
		});
	}

	@Test
	void aModernStdioConnectionWorksWithTheAsynchronousClientType() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.withPropertyValues(testServer("files", "protocol=MODERN"))
			.run(context -> {
				assertThat(context).hasNotFailed();

				List<AsyncModernMcpConnection> connections = named(context.getBean("modernMcpAsyncConnections"));
				assertThat(connections).hasSize(1);
				assertThat(connections.get(0).clientPerRequest()).isTrue();

				ToolCallback[] tools = context.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(callback(tools, "files_echo").call("{\"text\":\"hello\"}")).contains("echo: hello");
			});
	}

	@Test
	void theClientScopeAndTheListenStreamOfAStdioConnectionAreConfigured() {
		this.contextRunner.withUserConfiguration(RecordingListenerConfiguration.class)
			.withPropertyValues(testServer("files", "protocol=MODERN", "client-scope=SHARED", "listen=true"))
			.run(context -> {
				assertThat(context).hasNotFailed();

				List<SyncModernMcpConnection> connections = named(context.getBean("modernMcpSyncConnections"));
				assertThat(connections.get(0).client()).isSameAs(connections.get(0).client());
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class).connectionNames())
					.containsExactly("files");

				RecordingListener listener = context.getBean(RecordingListener.class);
				await().atMost(Duration.ofSeconds(10))
					.untilAsserted(() -> assertThat(listener.heard).contains("established files"));

				ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
				assertThat(names(provider.getToolCallbacks())).doesNotContain("files_added");

				callback(provider.getToolCallbacks(), "files_add_tool").call("{}");

				// The shared client keeps the private listing: only the report of the
				// server on the listen stream makes the new tool visible.
				await().atMost(Duration.ofSeconds(10))
					.untilAsserted(() -> assertThat(listener.heard).contains("changed files"));
				assertThat(names(provider.getToolCallbacks())).contains("files_added");
			});
	}

	@Test
	void theServerProcessesAreEndedWhenTheContextIsClosed() {
		Set<Long> before = childProcessIds();
		List<ProcessHandle> serverProcesses = new ArrayList<>();

		this.contextRunner.withPropertyValues(testServer("files", "protocol=MODERN"))
			.withPropertyValues(testServer("notes", "protocol=MODERN"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				serverProcesses.addAll(startedSince(before));
				assertThat(serverProcesses).hasSize(2);
			});

		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(serverProcesses).noneMatch(ProcessHandle::isAlive));
	}

	@Test
	void aTransportBeanOfTheApplicationIsServedNextToTheConfiguredConnections() {
		this.contextRunner.withUserConfiguration(ApplicationTransportConfiguration.class)
			.withPropertyValues(testServer("files", "protocol=MODERN"))
			.run(context -> {
				assertThat(context).hasNotFailed();

				ToolCallback[] tools = context.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(names(tools)).contains("files_echo", "notes_echo");
			});
	}

	@Test
	void theToolsOfAServerProcessThatEndedAreLeftOut(CapturedOutput output) {
		Set<Long> before = childProcessIds();

		this.contextRunner.withPropertyValues(testServer("files", "protocol=MODERN")).run(context -> {
			ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
			assertThat(names(provider.getToolCallbacks())).contains("files_echo");

			List<ProcessHandle> serverProcesses = startedSince(before);
			assertThat(serverProcesses).hasSize(1);
			serverProcesses.forEach(ProcessHandle::destroy);
			await().atMost(Duration.ofSeconds(10))
				.untilAsserted(() -> assertThat(serverProcesses).noneMatch(ProcessHandle::isAlive));

			// As for a server that cannot be reached: no failure, and no new process.
			assertThat(provider.getToolCallbacks()).isEmpty();
			assertThat(output).contains("The MCP server of the connection 'files' cannot be reached");
			assertThat(startedSince(before)).isEmpty();
		});
	}

	@Test
	void aServerProcessThatCannotBeStartedFailsTheStartup() {
		this.contextRunner.withPropertyValues(FILES + "command=/no/such/mcp-server-command", FILES + "protocol=MODERN")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.hasStackTraceContaining("The server process of the MCP connection 'files' could not be started")
				.hasStackTraceContaining("/no/such/mcp-server-command"));
	}

	@Test
	void theServerProcessesStartedBeforeAFailedStartAreEnded() {
		Set<Long> before = childProcessIds();
		List<String> command = ModernStdioTestServer.command();

		// In this order: the first process is running when the second cannot be started.
		Map<String, Parameters> connections = new LinkedHashMap<>();
		connections.put("files", new Parameters(command.get(0), command.subList(1, command.size()), null,
				McpClientCommonProperties.Protocol.MODERN, null, null));
		connections.put("broken", new Parameters("/no/such/mcp-server-command", null, null,
				McpClientCommonProperties.Protocol.MODERN, null, null));

		assertThatThrownBy(() -> ModernStdioTransports.start(connections, new JacksonMcpJsonMapper(JsonMapper.shared()),
				List.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("The server process of the MCP connection 'broken' could not be started");

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(startedSince(before)).isEmpty());
	}

	@Test
	void aModernConnectionOfTheServersConfigurationResourceIsServed(@TempDir Path directory) throws IOException {
		List<String> command = ModernStdioTestServer.command();
		Path serversConfiguration = directory.resolve("mcp-servers.json");
		Files.writeString(serversConfiguration,
				JsonMapper.shared()
					.writeValueAsString(Map.of("mcpServers", Map.of("files", Map.of("command", command.get(0), "args",
							command.subList(1, command.size()), "protocol", "MODERN")))));

		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.servers-configuration=" + serversConfiguration.toUri())
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(named(context.getBean("stdioTransports"))).isEmpty();

				ToolCallback[] tools = context.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(callback(tools, "files_echo").call("{\"text\":\"hello\"}")).contains("echo: hello");
			});
	}

	@Test
	void transportCustomizersAreAppliedBeforeTheServerProcessIsStarted() {
		this.contextRunner.withUserConfiguration(RecordingTransportCustomizerConfiguration.class)
			.withPropertyValues(testServer("files", "protocol=MODERN"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(RecordingTransportCustomizerConfiguration.class).customized)
					.containsExactly("files");
			});
	}

	@Test
	void nothingIsStartedWhenTheMcpClientIsDisabled() {
		Set<Long> before = childProcessIds();

		this.contextRunner.withPropertyValues("spring.ai.mcp.client.enabled=false")
			.withPropertyValues(testServer("files", "protocol=MODERN"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("modernStdioServerProcesses");
				assertThat(startedSince(before)).isEmpty();
			});
	}

	@Test
	void aNameUsedByAModernConnectionOfBothTransportsFailsTheStartup() {
		Set<Long> before = childProcessIds();

		this.contextRunner.withPropertyValues(testServer("files", "protocol=MODERN"))
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.files.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.files.protocol=MODERN")
			.run(context -> {
				assertThat(context).hasFailed()
					.getFailure()
					.hasStackTraceContaining("The MCP connection name 'files' is used by a modern Streamable HTTP "
							+ "connection and by a modern STDIO connection");
				// Checked before any process is started.
				assertThat(startedSince(before)).isEmpty();
			});
	}

	@Test
	void aLegacyStdioConnectionThatSetsListenIsWarnedAbout(CapturedOutput output) {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.initialized=false", FILES + "command=echo", FILES + "listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(named(context.getBean("stdioTransports"))).hasSize(1);
				assertThat(named(context.getBean("modernStdioConnectionTransports"))).isEmpty();
				assertThat(output).contains("The MCP connection 'files' sets listen=true but does not use the MODERN");
			});
	}

	/**
	 * The properties of a STDIO connection that starts {@link ModernStdioTestServer}.
	 */
	private static String[] testServer(String name, String... settings) {
		String prefix = "spring.ai.mcp.client.stdio.connections." + name + ".";
		List<String> command = ModernStdioTestServer.command();
		List<String> properties = new ArrayList<>();
		properties.add(prefix + "command=" + command.get(0));
		for (int i = 1; i < command.size(); i++) {
			properties.add(prefix + "args[" + (i - 1) + "]=" + command.get(i));
		}
		for (String setting : settings) {
			properties.add(prefix + setting);
		}
		return properties.toArray(String[]::new);
	}

	/**
	 * The ids of the child processes of this JVM, to tell the server processes of a test
	 * from whatever else may run.
	 */
	private static Set<Long> childProcessIds() {
		return ProcessHandle.current().children().map(ProcessHandle::pid).collect(Collectors.toSet());
	}

	private static List<ProcessHandle> startedSince(Set<Long> before) {
		return ProcessHandle.current()
			.children()
			.filter(process -> !before.contains(process.pid()))
			.filter(ProcessHandle::isAlive)
			.toList();
	}

	private static List<String> names(ToolCallback[] tools) {
		return Arrays.stream(tools).map(tool -> tool.getToolDefinition().name()).toList();
	}

	private static ToolCallback callback(ToolCallback[] tools, String name) {
		return Arrays.stream(tools)
			.filter(tool -> tool.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> named(Object bean) {
		return (List<T>) bean;
	}

	static class RecordingListener implements SyncModernMcpChangeListener {

		final List<String> heard = new CopyOnWriteArrayList<>();

		@Override
		public void changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
			this.heard.add("changed " + connectionInfo.name());
		}

		@Override
		public void streamEstablished(McpModernConnectionInfo connectionInfo) {
			this.heard.add("established " + connectionInfo.name());
		}

	}

	/**
	 * A transport of the application, in a bean that is named as the reference
	 * documentation of 2.1.0 names it.
	 */
	@Configuration(proxyBeanMethods = false)
	static class ApplicationTransportConfiguration {

		@Bean
		StdioMcpClientTransport notesTransport() {
			List<String> command = ModernStdioTestServer.command();
			return StdioMcpClientTransport
				.builder(ServerParameters.builder(command.get(0)).args(command.subList(1, command.size())).build())
				.start();
		}

		@Bean
		List<NamedModernClientMcpTransport> modernStdioTransports(StdioMcpClientTransport notesTransport) {
			return List.of(new NamedModernClientMcpTransport("notes", notesTransport));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class RecordingTransportCustomizerConfiguration {

		final List<String> customized = new CopyOnWriteArrayList<>();

		@Bean
		McpClientCustomizer<StdioMcpClientTransport.Builder> recordingTransportCustomizer() {
			return (connectionName, builder) -> this.customized.add(connectionName);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class RecordingListenerConfiguration {

		@Bean
		RecordingListener recordingListener() {
			return new RecordingListener();
		}

	}

}
