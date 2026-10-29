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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.HmacRequestStateCodec;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.customizer.McpModernServerCustomizer;
import org.springframework.ai.mcp.modern.InMemorySyncCompletionRepository;
import org.springframework.ai.mcp.modern.InMemorySyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemorySyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.McpTransportContexts;
import org.springframework.ai.mcp.modern.McpVisibilityFilter;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerModernProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpServerModernAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				ModernServerSpecificationFactoryAutoConfiguration.class,
				ModernToolCallbackConverterAutoConfiguration.class, McpServerModernAutoConfiguration.class));

	private static Map<String, Object> params(Object... keyValues) {
		Map<String, Object> params = new HashMap<>();
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		params.put("_meta", meta);
		for (int i = 0; i < keyValues.length; i += 2) {
			params.put((String) keyValues[i], keyValues[i + 1]);
		}
		return params;
	}

	private static Map<String, Object> call(McpServer server, String method, Map<String, Object> params) {
		return call(server, McpTransportContext.EMPTY, method, params);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> call(McpServer server, McpTransportContext transportContext, String method,
			Map<String, Object> params) {
		McpTransportResponse response = server.handleBlocking(transportContext, new JSONRPCRequest(method, 1, params))
			.block();
		assertThat(response).isInstanceOf(McpTransportResponse.Result.class);
		return (Map<String, Object>) ((McpTransportResponse.Result) response).response().result();
	}

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpServer.class);
			assertThat(context).hasSingleBean(McpChangeFeed.class);
			assertThat(context).hasSingleBean(InMemorySyncToolRepository.class);
			assertThat(context).hasSingleBean(InMemorySyncResourceRepository.class);
			assertThat(context).hasSingleBean(InMemorySyncPromptRepository.class);
			assertThat(context).hasSingleBean(InMemorySyncCompletionRepository.class);
			assertThat(context.getBean(InMemorySyncToolRepository.class).tools()).isEmpty();

			McpServerModernProperties properties = context.getBean(McpServerModernProperties.class);
			assertThat(properties.getMcpEndpoint()).isEqualTo("/mcp");
			assertThat(properties.getSupportedVersions()).containsExactly(McpSchema.LATEST_PROTOCOL_VERSION);

			Map<String, Object> discover = call(context.getBean(McpServer.class), McpSchema.METHOD_SERVER_DISCOVER,
					params());
			assertThat(discover.get("supportedVersions")).isEqualTo(List.of(McpSchema.LATEST_PROTOCOL_VERSION));
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void annotatedBeansAndToolCallbacksAreServed() {
		this.contextRunner.withUserConfiguration(UserConfiguration.class).run(context -> {
			InMemorySyncToolRepository tools = context.getBean(InMemorySyncToolRepository.class);
			assertThat(tools.tools()).extracting(McpSchema.Tool::name)
				.containsExactly("admin_reset", "greet", "weather");
			assertThat(context.getBean(InMemorySyncResourceRepository.class).resources()).hasSize(1);
			assertThat(context.getBean(InMemorySyncPromptRepository.class).prompts()).hasSize(1);

			McpServer server = context.getBean(McpServer.class);
			Map<String, Object> greet = call(server, McpSchema.METHOD_TOOLS_CALL,
					params("name", "greet", "arguments", Map.of("name", "Ann")));
			List<Map<String, Object>> content = (List<Map<String, Object>>) greet.get("content");
			assertThat(content.get(0).get("text")).isEqualTo("Hello Ann");

			Map<String, Object> weather = call(server, McpSchema.METHOD_TOOLS_CALL,
					params("name", "weather", "arguments", Map.of("city", "Paris")));
			content = (List<Map<String, Object>>) weather.get("content");
			assertThat(content.get(0).get("text")).isEqualTo("\"sunny in Paris\"");
		});
	}

	@Test
	void customizerIsApplied() {
		this.contextRunner.withUserConfiguration(CustomizerConfiguration.class).run(context -> {
			Map<String, Object> discover = call(context.getBean(McpServer.class), McpSchema.METHOD_SERVER_DISCOVER,
					params());
			assertThat(discover.get("instructions")).isEqualTo("customized");
		});
	}

	@Test
	void propertiesAreApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.name=modern-server", "spring.ai.mcp.server.version=2.0.0",
					"spring.ai.mcp.server.instructions=Use me", "spring.ai.mcp.server.modern.discover-ttl=5s",
					"spring.ai.mcp.server.modern.discover-cache-scope=PUBLIC",
					"spring.ai.mcp.server.modern.request-state-secret=shared-secret",
					"spring.ai.mcp.server.modern.request-state-ttl=10m", "spring.ai.mcp.server.modern.list-ttl=2s")
			.run(context -> {
				Map<String, Object> discover = call(context.getBean(McpServer.class), McpSchema.METHOD_SERVER_DISCOVER,
						params());
				assertThat(discover.get("instructions")).isEqualTo("Use me");
				assertThat(discover.get("ttlMs")).isEqualTo(5000L);
				assertThat(discover.get("cacheScope")).isEqualTo("public");
				assertThat(((Map<?, ?>) discover.get("_meta")).get(MetaKeys.SERVER_INFO)).isNotNull();

				Map<String, Object> list = call(context.getBean(McpServer.class), McpSchema.METHOD_TOOLS_LIST,
						params());
				assertThat(list.get("ttlMs")).isEqualTo(2000L);
			});
	}

	@Test
	void capabilitiesCanBeDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.capabilities.tool=false",
					"spring.ai.mcp.server.capabilities.completion=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean(InMemorySyncToolRepository.class);
				assertThat(context).doesNotHaveBean(InMemorySyncCompletionRepository.class);
				assertThat(context).hasSingleBean(InMemorySyncResourceRepository.class);
				assertThat(context).hasSingleBean(McpServer.class);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void visibilityFilterBeansRestrictTheInMemoryRepositoriesPerCaller() {
		this.contextRunner.withUserConfiguration(UserConfiguration.class, VisibilityConfiguration.class)
			.run(context -> {
				McpServer server = context.getBean(McpServer.class);
				McpTransportContext admin = McpTransportContexts
					.fromHeaders(each -> each.accept("X-Role", List.of("admin")));

				// Guests see and reach only the public tool; the listing is private.
				Map<String, Object> guestList = call(server, McpTransportContext.EMPTY, McpSchema.METHOD_TOOLS_LIST,
						params());
				assertThat((List<Map<String, Object>>) guestList.get("tools")).extracting(tool -> tool.get("name"))
					.containsExactly("greet");
				assertThat(guestList.get("cacheScope")).isEqualTo("private");
				McpTransportResponse denied = server
					.handleBlocking(McpTransportContext.EMPTY,
							new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1,
									params("name", "admin_reset", "arguments", Map.of())))
					.block();
				assertThat(denied).isInstanceOf(McpTransportResponse.Error.class);
				assertThat(((McpTransportResponse.Error) denied).response().error().message()).contains("Unknown tool");

				// The admin header unlocks everything, including the prompt and the
				// resource.
				Map<String, Object> adminList = call(server, admin, McpSchema.METHOD_TOOLS_LIST, params());
				assertThat((List<Map<String, Object>>) adminList.get("tools")).extracting(tool -> tool.get("name"))
					.containsExactly("admin_reset", "greet", "weather");
				assertThat((List<?>) call(server, McpTransportContext.EMPTY, McpSchema.METHOD_PROMPTS_LIST, params())
					.get("prompts")).isEmpty();
				assertThat((List<?>) call(server, admin, McpSchema.METHOD_PROMPTS_LIST, params()).get("prompts"))
					.hasSize(1);
				assertThat((List<?>) call(server, McpTransportContext.EMPTY, McpSchema.METHOD_RESOURCES_LIST, params())
					.get("resources")).isEmpty();
				assertThat((List<?>) call(server, admin, McpSchema.METHOD_RESOURCES_LIST, params()).get("resources"))
					.hasSize(1);

				// Completing an argument of the hidden prompt would reveal it.
				Map<String, Object> completion = params("ref", Map.of("type", "ref/prompt", "name", "p"), "argument",
						Map.of("name", "topic", "value", "a"));
				McpTransportResponse hiddenCompletion = server
					.handleBlocking(McpTransportContext.EMPTY,
							new JSONRPCRequest(McpSchema.METHOD_COMPLETION_COMPLETE, 1, completion))
					.block();
				assertThat(hiddenCompletion).isInstanceOf(McpTransportResponse.Error.class);
				assertThat(call(server, admin, McpSchema.METHOD_COMPLETION_COMPLETE, completion))
					.containsKey("completion");
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void customRepositoryReplacesInMemoryRepository() {
		this.contextRunner.withUserConfiguration(UserConfiguration.class, CustomRepositoryConfiguration.class)
			.run(context -> {
				assertThat(context).doesNotHaveBean(InMemorySyncToolRepository.class);
				assertThat(context).hasSingleBean(McpSyncToolRepository.class);
				assertThat(context).hasSingleBean(InMemorySyncResourceRepository.class);
				assertThat(context).hasSingleBean(InMemorySyncPromptRepository.class);

				McpServer server = context.getBean(McpServer.class);
				Map<String, Object> list = call(server, McpSchema.METHOD_TOOLS_LIST, params());
				List<Map<String, Object>> tools = (List<Map<String, Object>>) list.get("tools");
				assertThat(tools).extracting(tool -> tool.get("name")).containsExactly("custom");

				Map<String, Object> result = call(server, McpSchema.METHOD_TOOLS_CALL,
						params("name", "custom", "arguments", Map.of()));
				List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
				assertThat(content.get(0).get("text")).isEqualTo("from custom repository");
			});
	}

	@Test
	void inactiveForOtherProtocolsAndWhenDisabled() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
			.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
					McpServerModernAutoConfiguration.class))
			.run(context -> assertThat(context).doesNotHaveBean(McpServer.class));
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(McpServer.class));
	}

	@Test
	void legacyAndStatelessAutoConfigurationsBackOffForModernProtocol() {
		// Every server auto-configuration of this module, as listed in
		// META-INF/spring/...AutoConfiguration.imports, and both user bean kinds.
		ApplicationContextRunner all = new ApplicationContextRunner().withUserConfiguration(UserConfiguration.class)
			.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
					McpServerJsonMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class,
					McpServerStatelessAutoConfiguration.class, StatelessToolCallbackConverterAutoConfiguration.class,
					McpServerSpecificationFactoryAutoConfiguration.class,
					StatelessServerSpecificationFactoryAutoConfiguration.class,
					McpServerAnnotationScannerAutoConfiguration.class, McpServerModernAutoConfiguration.class,
					ModernToolCallbackConverterAutoConfiguration.class,
					ModernServerSpecificationFactoryAutoConfiguration.class));

		all.withPropertyValues("spring.ai.mcp.server.protocol=MODERN").run(context -> {
			assertThat(context).hasNotFailed();
			// Modern side is active and sees the annotated bean and the ToolCallback.
			assertThat(context).hasSingleBean(McpServer.class);
			assertThat(context).hasBean("modernToolSpecs");
			assertThat(context.getBean(InMemorySyncToolRepository.class).tools()).hasSize(3);
			// Legacy and stateless sides are fully inactive: no servers, no spec
			// factories, no tool callback converters.
			assertThat(context).doesNotHaveBean(io.modelcontextprotocol.server.McpSyncServer.class);
			assertThat(context).doesNotHaveBean(io.modelcontextprotocol.server.McpAsyncServer.class);
			assertThat(context).doesNotHaveBean(io.modelcontextprotocol.server.McpStatelessSyncServer.class);
			assertThat(context).doesNotHaveBean(McpServerSpecificationFactoryAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(StatelessServerSpecificationFactoryAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(ToolCallbackConverterAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).doesNotHaveBean("toolSpecs");
			assertThat(context).doesNotHaveBean("resourceSpecs");
			assertThat(context.getBeansOfType(List.class).keySet()).allMatch(name -> name.startsWith("modern"),
					"only modern specification lists expected");
		});

		// And the reverse: a legacy protocol never activates the modern side.
		all.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE").run(context -> {
			assertThat(context).doesNotHaveBean(McpServer.class);
			assertThat(context).doesNotHaveBean(ModernServerSpecificationFactoryAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(ModernToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasSingleBean(McpServerSpecificationFactoryAutoConfiguration.class);
		});
	}

	@Test
	void toolCallbackConverterFlagDoesNotAffectAnnotatedPrimitives() {
		this.contextRunner.withUserConfiguration(UserConfiguration.class)
			.withPropertyValues("spring.ai.mcp.server.tool-callback-converter=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(ModernToolCallbackConverterAutoConfiguration.class);
				assertThat(context).hasBean("modernToolSpecs");
				assertThat(context).hasBean("modernResourceSpecs");
				assertThat(context).hasBean("modernPromptSpecs");
				// Only the annotated tools are published; the ToolCallback bean is not.
				assertThat(context.getBean(InMemorySyncToolRepository.class).tools()).hasSize(2);
				Map<String, Object> list = call(context.getBean(McpServer.class), McpSchema.METHOD_TOOLS_LIST,
						params());
				List<Map<String, Object>> tools = (List<Map<String, Object>>) list.get("tools");
				assertThat(tools).extracting(tool -> tool.get("name")).doesNotContain("weather");
			});
	}

	@Test
	void annotationFactoryBacksOffWithoutAnnotationsModule() {
		this.contextRunner.withUserConfiguration(ToolCallbackOnlyConfiguration.class)
			.withClassLoader(new FilteredClassLoader(McpTool.class))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(ModernServerSpecificationFactoryAutoConfiguration.class);
				assertThat(context).hasSingleBean(McpServer.class);
				assertThat(context.getBean(InMemorySyncToolRepository.class).tools()).extracting(Tool::name)
					.containsExactly("weather");
			});
	}

	@Test
	void requestStateTtlIsHonouredWithoutSecret() {
		McpServerModernProperties properties = new McpServerModernProperties();
		properties.setRequestStateTtl(Duration.ofMinutes(1));
		RequestStateCodec codec = McpServerModernAutoConfiguration.requestStateCodec(properties,
				McpJsonDefaults.getMapper());
		assertThat(codec).isInstanceOf(HmacRequestStateCodec.class).extracting("ttl").isEqualTo(Duration.ofMinutes(1));

		properties.setRequestStateSecret("shared-secret");
		assertThat(McpServerModernAutoConfiguration.requestStateCodec(properties, McpJsonDefaults.getMapper()))
			.extracting("ttl")
			.isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	void asyncTypeFailsFast() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("type=ASYNC is not supported");
		});
	}

	@Configuration
	static class ToolCallbackOnlyConfiguration {

		@Bean
		ToolCallback weatherTool() {
			return FunctionToolCallback
				.builder("weather", (Function<WeatherRequest, String>) request -> "sunny in " + request.city())
				.description("Weather")
				.inputType(WeatherRequest.class)
				.build();
		}

	}

	@Configuration
	static class UserConfiguration {

		@Bean
		AnnotatedBean annotatedBean() {
			return new AnnotatedBean();
		}

		@Bean
		ToolCallback weatherTool() {
			return FunctionToolCallback
				.builder("weather", (Function<WeatherRequest, String>) request -> "sunny in " + request.city())
				.description("Weather")
				.inputType(WeatherRequest.class)
				.build();
		}

	}

	@Configuration
	static class CustomizerConfiguration {

		@Bean
		McpModernServerCustomizer customizer() {
			return builder -> builder.instructions("customized");
		}

	}

	@Configuration
	static class VisibilityConfiguration {

		@Bean
		McpVisibilityFilter adminOnlyUnlessPublic() {
			// One rule for every primitive: admins see all, others only what is public.
			return new McpVisibilityFilter() {
				@Override
				public boolean tool(McpRequestContext ctx, Tool tool) {
					return admin(ctx) || "greet".equals(tool.name());
				}

				@Override
				public boolean resource(McpRequestContext ctx, McpSchema.Resource resource) {
					return admin(ctx);
				}

				@Override
				public boolean prompt(McpRequestContext ctx, McpSchema.Prompt prompt) {
					return admin(ctx);
				}

				private boolean admin(McpRequestContext ctx) {
					return "admin".equals(McpTransportContexts.header(ctx, "x-role"));
				}
			};
		}

	}

	@Configuration
	static class CustomRepositoryConfiguration {

		@Bean
		McpSyncToolRepository customToolRepository() {
			return new CustomToolRepository();
		}

	}

	record WeatherRequest(String city) {
	}

	static class CustomToolRepository implements McpSyncToolRepository {

		private static final McpSchema.Tool TOOL = McpSchema.Tool.builder("custom", Map.of("type", "object"))
			.description("Custom")
			.build();

		@Override
		public ToolsPage list(McpRequestContext ctx, String cursor) {
			return ToolsPage.of(List.of(TOOL));
		}

		@Override
		public McpSchema.Tool find(McpRequestContext ctx, String name) {
			return TOOL.name().equals(name) ? TOOL : null;
		}

		@Override
		public McpSyncResponse<McpSchema.CallToolOutcome> call(McpRequestContext ctx,
				McpSchema.CallToolRequest request) {
			return McpSyncResponse.result(McpSchema.CallToolResult.builder()
				.addContent(McpSchema.TextContent.builder("from custom repository").build())
				.build());
		}

	}

	static class AnnotatedBean {

		@McpTool(name = "greet", description = "Greets")
		public String greet(@McpToolParam(description = "name") String name) {
			return "Hello " + name;
		}

		@McpTool(name = "admin_reset", description = "Administrative")
		public String adminReset() {
			return "reset";
		}

		@McpResource(uri = "test://doc", name = "doc")
		public String doc() {
			return "doc";
		}

		@McpPrompt(name = "p", description = "p")
		public String prompt() {
			return "prompt";
		}

		@McpComplete(prompt = "p")
		public List<String> completeTopic(String value) {
			return List.of(value + "1");
		}

	}

}
