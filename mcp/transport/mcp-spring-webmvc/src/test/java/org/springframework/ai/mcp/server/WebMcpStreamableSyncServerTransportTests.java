/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.mcp.server;

import io.modelcontextprotocol.server.AbstractMcpSyncServerTests;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.TestUtil;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Timeout;
import reactor.netty.DisposableServer;

import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Tests for {@link McpSyncServer} using {@link WebMvcStreamableServerTransportProvider}.
 *
 * @author Christian Tzolov
 */
@Timeout(15) // Giving extra time beyond the client timeout
class WebMcpStreamableSyncServerTransportTests extends AbstractMcpSyncServerTests {

	private static final int PORT = TestUtil.findAvailablePort();

	private static final String MCP_ENDPOINT = "/mcp";

	private DisposableServer httpServer;

	private AnnotationConfigWebApplicationContext appContext;

	private Tomcat tomcat;

	private McpStreamableServerTransportProvider transportProvider;

	private McpStreamableServerTransportProvider createMcpTransportProvider() {
		// Set up Tomcat first
		this.tomcat = new Tomcat();
		this.tomcat.setPort(PORT);

		// Set Tomcat base directory to java.io.tmpdir to avoid permission issues
		String baseDir = System.getProperty("java.io.tmpdir");
		this.tomcat.setBaseDir(baseDir);

		// Use the same directory for document base
		Context context = this.tomcat.addContext("", baseDir);

		// Create and configure Spring WebMvc context
		this.appContext = new AnnotationConfigWebApplicationContext();
		this.appContext.register(TestConfig.class);
		this.appContext.setServletContext(context.getServletContext());
		this.appContext.refresh();

		// Get the transport from Spring context
		this.transportProvider = this.appContext.getBean(McpStreamableServerTransportProvider.class);

		// Create DispatcherServlet with our Spring context
		DispatcherServlet dispatcherServlet = new DispatcherServlet(this.appContext);

		// Add servlet to Tomcat and get the wrapper
		var wrapper = Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
		wrapper.setLoadOnStartup(1);
		context.addServletMappingDecoded("/*", "dispatcherServlet");

		try {
			this.tomcat.start();
			this.tomcat.getConnector(); // Create and start the connector
		}
		catch (LifecycleException e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}

		return this.transportProvider;
	}

	@Override
	protected McpServer.SyncSpecification<?> prepareSyncServerBuilder() {
		return McpServer.sync(createMcpTransportProvider());
	}

	@Override
	protected void onStart() {
	}

	@Override
	protected void onClose() {
		if (this.httpServer != null) {
			this.httpServer.disposeNow();
		}
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcStreamableServerTransportProvider webMvcSseServerTransportProvider() {
			return WebMvcStreamableServerTransportProvider.builder().mcpEndpoint(MCP_ENDPOINT).build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(
				WebMvcStreamableServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
