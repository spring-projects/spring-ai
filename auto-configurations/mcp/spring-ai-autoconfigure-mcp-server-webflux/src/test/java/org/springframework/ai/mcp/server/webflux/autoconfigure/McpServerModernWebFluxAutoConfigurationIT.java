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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.modern.McpTransportContexts;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.modern.WebFluxMcpTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpServerModernWebFluxAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerModernAutoConfiguration.class, McpServerModernWebFluxAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxMcpTransport.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
			assertThat(context).hasBean("webFluxModernServerRouterFunction");
			assertThat(context).hasSingleBean(SmartLifecycle.class);
			assertThat(routedStatus(context.getBean(RouterFunction.class), "/mcp")).isNotEqualTo(404);
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.modern.mcp-endpoint=/api/mcp").run(context -> {
			RouterFunction<?> router = context.getBean(RouterFunction.class);
			assertThat(routedStatus(router, "/api/mcp")).isNotEqualTo(404);
			assertThat(routedStatus(router, "/mcp")).isEqualTo(404);
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int routedStatus(RouterFunction<?> router, String path) {
		return WebTestClient.bindToRouterFunction((RouterFunction) router)
			.build()
			.post()
			.uri(path)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{}")
			.exchange()
			.returnResult(String.class)
			.getStatus()
			.value();
	}

	@Test
	void requestHeadersReachTheTransportContextByDefault() {
		this.contextRunner.run(context -> {
			Object extractor = ReflectionTestUtils.getField(context.getBean(WebFluxMcpTransport.class),
					"contextExtractor");
			assertThat(extractor).isInstanceOf(HeaderTransportContextExtractor.class);

			ServerRequest request = MockServerRequest.builder()
				.header("X-Role", "admin")
				.header("Mcp-Method", "tools/list")
				.build();
			McpTransportContext transportContext = ((HeaderTransportContextExtractor) extractor).extract(request);
			assertThat(McpTransportContexts.header(transportContext, "x-role")).isEqualTo("admin");
			assertThat(McpTransportContexts.headers(transportContext)).doesNotContainKey("mcp-method");
		});
	}

	@Test
	void userExtractorReplacesTheDefault() {
		this.contextRunner.withUserConfiguration(ExtractorConfiguration.class)
			.run(context -> assertThat(context.getBean(WebFluxMcpTransport.class)).extracting("contextExtractor")
				.isSameAs(context.getBean(McpTransportContextExtractor.class)));
	}

	@Test
	void inactiveWhenDisabledOrOtherProtocolOrStdio() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true")
			.run(context -> assertThat(context).doesNotHaveBean(WebFluxMcpTransport.class));
	}

	@Configuration
	static class ExtractorConfiguration {

		@Bean
		McpTransportContextExtractor<ServerRequest> tenantExtractor() {
			return request -> McpTransportContext.create(Map.of("tenant", "acme"));
		}

	}

}
