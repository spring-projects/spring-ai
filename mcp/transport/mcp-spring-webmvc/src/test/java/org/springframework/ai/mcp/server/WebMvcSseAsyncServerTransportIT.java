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

import io.modelcontextprotocol.server.AbstractMcpAsyncServerTests;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Timeout(15)
class WebMvcSseAsyncServerTransportIT extends AbstractMcpAsyncServerTests {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private Tomcat tomcat;

	private McpServerTransportProvider transportProvider;

	private AnnotationConfigWebApplicationContext appContext;

	private McpServerTransportProvider createMcpTransportProvider() {
		// Set up Tomcat first
		this.tomcat = new Tomcat();
		this.tomcat.setPort(0);

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
		this.transportProvider = this.appContext.getBean(WebMvcSseServerTransportProvider.class);

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
	protected McpServer.AsyncSpecification<?> prepareAsyncServerBuilder() {
		return McpServer.async(createMcpTransportProvider());
	}

	@Override
	protected void onStart() {
	}

	@Override
	protected void onClose() {
		if (this.transportProvider != null) {
			this.transportProvider.closeGracefully().block();
		}
		if (this.appContext != null) {
			this.appContext.close();
		}
		if (this.tomcat != null) {
			try {
				this.tomcat.stop();
				this.tomcat.destroy();
			}
			catch (LifecycleException e) {
				throw new RuntimeException("Failed to stop Tomcat", e);
			}
		}
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
			return WebMvcSseServerTransportProvider.builder()
				.messageEndpoint(MESSAGE_ENDPOINT)
				.sseEndpoint(WebMvcSseServerTransportProvider.DEFAULT_SSE_ENDPOINT)
				.build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
