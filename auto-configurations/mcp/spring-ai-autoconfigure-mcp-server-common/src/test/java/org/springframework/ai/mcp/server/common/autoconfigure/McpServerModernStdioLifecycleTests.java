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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration.StdioMcpTransportLifecycle;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the lifetime of a modern MCP server on the standard streams: the application
 * stays up while the input is open, and ends with it.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class McpServerModernStdioLifecycleTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN", "spring.ai.mcp.server.stdio=true")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				ModernServerSpecificationFactoryAutoConfiguration.class,
				ModernToolCallbackConverterAutoConfiguration.class, McpServerModernAutoConfiguration.class))
		.withUserConfiguration(PipedInputConfiguration.class);

	@Test
	void threadThatIsNotADaemonWaitsUntilTheInputEnds(CapturedOutput output) {
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			StdioMcpTransportLifecycle lifecycle = context.getBean(StdioMcpTransportLifecycle.class);
			Thread servingThread = lifecycle.servingThread();

			// The threads of the transport are daemon threads: this one keeps the JVM
			// alive while the client may still send a request.
			assertThat(servingThread).isNotNull();
			assertThat(servingThread.isDaemon()).isFalse();
			servingThread.join(100);
			assertThat(servingThread.isAlive()).isTrue();
			assertThat(lifecycle.isRunning()).isTrue();

			// The client closes its end of the input.
			context.getBean(PipedOutputStream.class).close();

			servingThread.join(10_000);
			assertThat(servingThread.isAlive()).isFalse();
			assertThat(lifecycle.isRunning()).isFalse();
			assertThat(output).contains("The standard input has ended");
		});
	}

	@Test
	void threadEndsWhenTheApplicationIsClosedWhileTheInputIsOpen(CapturedOutput output) throws InterruptedException {
		AtomicReference<Thread> servingThread = new AtomicReference<>();

		this.contextRunner.run(context -> {
			servingThread.set(context.getBean(StdioMcpTransportLifecycle.class).servingThread());
			assertThat(servingThread.get()).isNotNull();
			assertThat(servingThread.get().isAlive()).isTrue();
		});

		servingThread.get().join(10_000);
		assertThat(servingThread.get().isAlive()).isFalse();
		assertThat(output).doesNotContain("The standard input has ended");
	}

	@Test
	void lifecycleIsNotPauseableAsTheTransportStartsOnce() {
		this.contextRunner
			.run(context -> assertThat(context.getBean(StdioMcpTransportLifecycle.class).isPauseable()).isFalse());
	}

	/**
	 * The transport reads from a pipe in place of the standard input, so that a test can
	 * end the input.
	 */
	@Configuration(proxyBeanMethods = false)
	static class PipedInputConfiguration {

		@Bean
		PipedOutputStream clientEndOfTheInput() {
			return new PipedOutputStream();
		}

		@Bean
		StdioMcpTransport stdioTransport(McpServer mcpServer, PipedOutputStream clientEndOfTheInput)
				throws IOException {
			return new StdioMcpTransport(mcpServer, new JacksonMcpJsonMapper(JsonMapper.shared()),
					new PipedInputStream(clientEndOfTheInput), new ByteArrayOutputStream());
		}

	}

}
