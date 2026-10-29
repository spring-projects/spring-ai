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

package org.springframework.ai.mcp.server.webmvc.autoconfigure;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.modern.McpTransportContexts;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcModernServerTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpServerModernWebMvcAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerModernAutoConfiguration.class, McpServerModernWebMvcAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcModernServerTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasBean("webMvcModernServerRouterFunction");
			assertThat(context).hasSingleBean(SmartLifecycle.class);
			assertThat(context.getBean(WebMvcModernServerTransport.class)).extracting("mcpEndpoint").isEqualTo("/mcp");
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.mcp-endpoint=/api/mcp")
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class)).extracting("mcpEndpoint")
				.isEqualTo("/api/mcp"));
	}

	@Test
	void zeroKeepAliveIntervalDisablesKeepAlives() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.keep-alive-interval=0s")
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class))
				.extracting("keepAliveInterval")
				.isNull());
	}

	@Test
	void requestHeadersReachTheTransportContextByDefault() {
		this.contextRunner.run(context -> {
			Object extractor = ReflectionTestUtils.getField(context.getBean(WebMvcModernServerTransport.class),
					"contextExtractor");
			assertThat(extractor).isInstanceOf(HeaderTransportContextExtractor.class);

			MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp");
			servletRequest.addHeader("X-Role", "admin");
			servletRequest.addHeader("Mcp-Method", "tools/list");
			McpTransportContext transportContext = ((HeaderTransportContextExtractor) extractor)
				.extract(ServerRequest.create(servletRequest, List.of()));
			assertThat(McpTransportContexts.header(transportContext, "x-role")).isEqualTo("admin");
			assertThat(McpTransportContexts.headers(transportContext)).doesNotContainKey("mcp-method");
		});
	}

	@Test
	void userExtractorReplacesTheDefault() {
		this.contextRunner.withUserConfiguration(ExtractorConfiguration.class)
			.run(context -> assertThat(context.getBean(WebMvcModernServerTransport.class))
				.extracting("contextExtractor")
				.isSameAs(context.getBean(McpTransportContextExtractor.class)));
	}

	@Test
	void inactiveWhenDisabledOrOtherProtocolOrStdio() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebMvcModernServerTransport.class));
	}

	@Configuration
	static class ExtractorConfiguration {

		@Bean
		McpTransportContextExtractor<ServerRequest> tenantExtractor() {
			return request -> McpTransportContext.create(Map.of("tenant", "acme"));
		}

	}

}
