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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import io.modelcontextprotocol.modern.client.transport.McpTransportRequest;
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;

/**
 * The transports of the modern STDIO connections, with the server processes they talk to.
 * A modern client neither starts nor closes the transport it is given, so the processes
 * are started when this object is created, and ended when it is closed with the
 * application. A process that ends before that is not started again.
 *
 * @author Christian Tzolov
 * @since 2.1.1
 */
final class ModernStdioTransports implements AutoCloseable {

	private static final Log logger = LogFactory.getLog(ModernStdioTransports.class);

	/**
	 * The failure of an exchange on a transport that is closed, which is what the SDK
	 * transport is once its server process has ended.
	 */
	private static final String TRANSPORT_CLOSED = "The transport is closed";

	private final List<NamedModernClientMcpTransport> transports;

	private final List<StdioMcpClientTransport> started;

	private ModernStdioTransports(List<NamedModernClientMcpTransport> transports,
			List<StdioMcpClientTransport> started) {
		this.transports = List.copyOf(transports);
		this.started = List.copyOf(started);
	}

	/**
	 * Starts the server process of each modern connection.
	 * @param connections the STDIO connections by name; the legacy ones are skipped
	 * @param jsonMapper the JSON mapper of the transports
	 * @param customizers the customizers of the transport builders
	 * @return the started transports
	 * @throws IllegalStateException if a server process cannot be started; the processes
	 * started before it are ended
	 */
	static ModernStdioTransports start(Map<String, Parameters> connections, McpJsonMapper jsonMapper,
			List<McpClientCustomizer<StdioMcpClientTransport.Builder>> customizers) {

		List<NamedModernClientMcpTransport> transports = new ArrayList<>();
		List<StdioMcpClientTransport> started = new ArrayList<>();

		for (Map.Entry<String, Parameters> connection : connections.entrySet()) {

			if (!connection.getValue().isModern()) {
				// Legacy connections are served by StdioTransportAutoConfiguration.
				continue;
			}

			String name = connection.getKey();
			try {
				StdioMcpClientTransport.Builder transportBuilder = StdioMcpClientTransport
					.builder(connection.getValue().toServerParameters())
					.jsonMapper(jsonMapper);

				customizers.forEach(customizer -> customizer.customize(name, transportBuilder));

				StdioMcpClientTransport transport = transportBuilder.start();
				started.add(transport);
				transports
					.add(new NamedModernClientMcpTransport(name, new EndedProcessAwareTransport(name, transport)));
			}
			catch (RuntimeException ex) {
				close(started);
				throw new IllegalStateException(
						"The server process of the MCP connection '" + name + "' could not be started", ex);
			}
		}

		return new ModernStdioTransports(transports, started);
	}

	List<NamedModernClientMcpTransport> transports() {
		return this.transports;
	}

	/**
	 * Ends the server processes.
	 */
	@Override
	public void close() {
		close(this.started);
	}

	private static void close(List<StdioMcpClientTransport> transports) {
		for (StdioMcpClientTransport transport : transports) {
			try {
				transport.close();
			}
			catch (RuntimeException ex) {
				logger.warn("The server process of an MCP connection did not end cleanly", ex);
			}
		}
	}

	/**
	 * Reports a server process that has ended as an I/O failure, as a server that refuses
	 * the connection is reported for Streamable HTTP. The tool callback provider then
	 * leaves the tools of the connection out, and the tools of the other connections stay
	 * usable. The process is not started again.
	 */
	private static final class EndedProcessAwareTransport implements McpClientTransport {

		private final String connectionName;

		private final McpClientTransport delegate;

		EndedProcessAwareTransport(String connectionName, McpClientTransport delegate) {
			this.connectionName = connectionName;
			this.delegate = delegate;
		}

		@Override
		public Flux<JSONRPCMessage> exchange(McpTransportRequest request) {
			return this.delegate.exchange(request)
				.onErrorMap(ex -> ex instanceof IllegalStateException && TRANSPORT_CLOSED.equals(ex.getMessage()),
						ex -> new IOException(
								"The server process of the MCP connection '" + this.connectionName + "' has ended",
								ex));
		}

		@Override
		public boolean mirrorsToolParams() {
			return this.delegate.mirrorsToolParams();
		}

	}

}
