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

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CompleteReference;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.PromptReference;
import io.modelcontextprotocol.modern.McpSchema.ResourceReference;
import io.modelcontextprotocol.modern.server.HmacRequestStateCodec;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.feature.CompletionsFeature;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncCompletionRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.customizer.McpModernServerCustomizer;
import org.springframework.ai.mcp.modern.InMemorySyncCompletionRepository;
import org.springframework.ai.mcp.modern.InMemorySyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemorySyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.McpVisibilityFilter;
import org.springframework.ai.mcp.modern.SyncCompletionSpecification;
import org.springframework.ai.mcp.modern.SyncPromptSpecification;
import org.springframework.ai.mcp.modern.SyncResourceSpecification;
import org.springframework.ai.mcp.modern.SyncResourceTemplateSpecification;
import org.springframework.ai.mcp.modern.SyncToolSpecification;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerModernProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.Assert;

/**
 * {@link AutoConfiguration Auto-configuration} for a 2026-07-28 ("modern") MCP server,
 * active when {@code spring.ai.mcp.server.protocol=MODERN}.
 * <p>
 * Each primitive is served by a repository bean: {@link McpSyncToolRepository},
 * {@link McpSyncResourceRepository}, {@link McpSyncPromptRepository} and
 * {@link McpSyncCompletionRepository}. A user-defined repository bean of that type takes
 * precedence; otherwise an in-memory repository is created and fed by every
 * {@code List<SyncToolSpecification>}, {@code List<SyncResourceSpecification>},
 * {@code List<SyncResourceTemplateSpecification>}, {@code List<SyncPromptSpecification>}
 * and {@code List<SyncCompletionSpecification>} bean, which is how annotated beans and
 * {@code ToolCallback}s reach the server. The in-memory repositories are beans
 * themselves, so tools can be added or removed at runtime; changes are broadcast to
 * {@code subscriptions/listen} clients through the {@link McpChangeFeed} bean. Every
 * {@link McpVisibilityFilter} bean is applied to the in-memory repositories, so a caller
 * only sees, and can only reach, the primitives all filters allow; completions for a
 * hidden prompt or template are rejected as unknown. A user-defined repository bean
 * receives no filter automatically.
 * <p>
 * Only the synchronous API type is supported for now; {@code type=ASYNC} fails the
 * context at startup.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@AutoConfiguration(after = McpServerJsonMapperAutoConfiguration.class)
@ConditionalOnClass(McpServer.class)
@EnableConfigurationProperties({ McpServerProperties.class, McpServerModernProperties.class })
@Conditional(McpServerModernAutoConfiguration.EnabledModernServerCondition.class)
public class McpServerModernAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpServerModernAutoConfiguration.class);

	McpServerModernAutoConfiguration(McpServerProperties serverProperties) {
		// Fail fast rather than start without a server: the legacy auto-configurations
		// back off for protocol=MODERN, so silently skipping would leave nothing
		// listening.
		Assert.state(serverProperties.getType() == McpServerProperties.ApiType.SYNC,
				() -> "The MCP 2026-07-28 (MODERN) server supports only spring.ai.mcp.server.type=SYNC; " + "type="
						+ serverProperties.getType() + " is not supported yet");
	}

	@Bean
	@ConditionalOnMissingBean
	public McpChangeFeed mcpChangeFeed() {
		return new McpChangeFeed();
	}

	@Bean
	@ConditionalOnMissingBean(McpSyncToolRepository.class)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.tool", havingValue = "true",
			matchIfMissing = true)
	public InMemorySyncToolRepository mcpModernToolRepository(ObjectProvider<List<SyncToolSpecification>> tools,
			McpChangeFeed changeFeed, McpServerModernProperties modernProperties,
			ObjectProvider<McpVisibilityFilter> visibilityFilters) {
		List<SyncToolSpecification> specifications = tools.stream().flatMap(List::stream).toList();
		logger.info("Registered modern tools: " + specifications.size());
		InMemorySyncToolRepository.Builder builder = InMemorySyncToolRepository.builder()
			.tools(specifications)
			.changeBroadcaster(changeFeed)
			.pageSize(modernProperties.getPageSize());
		List<McpVisibilityFilter> filters = visibilityFilters.orderedStream().toList();
		if (!filters.isEmpty()) {
			builder.visibility(McpVisibilityFilter.allOf(filters));
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean(McpSyncResourceRepository.class)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.resource",
			havingValue = "true", matchIfMissing = true)
	public InMemorySyncResourceRepository mcpModernResourceRepository(
			ObjectProvider<List<SyncResourceSpecification>> resources,
			ObjectProvider<List<SyncResourceTemplateSpecification>> resourceTemplates, McpChangeFeed changeFeed,
			McpServerModernProperties modernProperties, ObjectProvider<McpVisibilityFilter> visibilityFilters) {
		List<SyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
		List<SyncResourceTemplateSpecification> templateSpecifications = resourceTemplates.stream()
			.flatMap(List::stream)
			.toList();
		logger.info("Registered modern resources: " + resourceSpecifications.size() + ", resource templates: "
				+ templateSpecifications.size());
		InMemorySyncResourceRepository.Builder builder = InMemorySyncResourceRepository.builder()
			.resources(resourceSpecifications)
			.resourceTemplates(templateSpecifications)
			.changeBroadcaster(changeFeed)
			.subscribeSupported(modernProperties.isResourceSubscribe())
			.pageSize(modernProperties.getPageSize());
		List<McpVisibilityFilter> filters = visibilityFilters.orderedStream().toList();
		if (!filters.isEmpty()) {
			builder.visibility(McpVisibilityFilter.allOf(filters));
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean(McpSyncPromptRepository.class)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.prompt",
			havingValue = "true", matchIfMissing = true)
	public InMemorySyncPromptRepository mcpModernPromptRepository(ObjectProvider<List<SyncPromptSpecification>> prompts,
			McpChangeFeed changeFeed, McpServerModernProperties modernProperties,
			ObjectProvider<McpVisibilityFilter> visibilityFilters) {
		List<SyncPromptSpecification> specifications = prompts.stream().flatMap(List::stream).toList();
		logger.info("Registered modern prompts: " + specifications.size());
		InMemorySyncPromptRepository.Builder builder = InMemorySyncPromptRepository.builder()
			.prompts(specifications)
			.changeBroadcaster(changeFeed)
			.pageSize(modernProperties.getPageSize());
		// Without filter beans the repository stays unfiltered and its listings
		// shareable.
		List<McpVisibilityFilter> filters = visibilityFilters.orderedStream().toList();
		if (!filters.isEmpty()) {
			builder.visibility(McpVisibilityFilter.allOf(filters));
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean(McpSyncCompletionRepository.class)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.completion",
			havingValue = "true", matchIfMissing = true)
	public InMemorySyncCompletionRepository mcpModernCompletionRepository(
			ObjectProvider<List<SyncCompletionSpecification>> completions,
			ObjectProvider<McpVisibilityFilter> visibilityFilters, ObjectProvider<McpSyncPromptRepository> prompts,
			ObjectProvider<McpSyncResourceRepository> resources) {
		List<SyncCompletionSpecification> specifications = completions.stream().flatMap(List::stream).toList();
		logger.info("Registered modern completions: " + specifications.size());
		InMemorySyncCompletionRepository.Builder builder = InMemorySyncCompletionRepository.builder()
			.completions(specifications);
		if (visibilityFilters.orderedStream().findAny().isPresent()) {
			// Completing a hidden prompt or template would reveal it, so a reference is
			// served only when the owning repository still finds it for this caller.
			builder.referenceGuard((ctx, reference) -> referenceVisible(ctx, reference, prompts, resources));
		}
		return builder.build();
	}

	private static boolean referenceVisible(McpRequestContext ctx, CompleteReference reference,
			ObjectProvider<McpSyncPromptRepository> prompts, ObjectProvider<McpSyncResourceRepository> resources) {
		if (reference instanceof PromptReference promptReference) {
			McpSyncPromptRepository repository = prompts.getIfAvailable();
			return repository == null || repository.find(ctx, promptReference.name()) != null;
		}
		if (reference instanceof ResourceReference resourceReference) {
			McpSyncResourceRepository repository = resources.getIfAvailable();
			if (repository == null) {
				return true;
			}
			String cursor = null;
			do {
				ResourceTemplatesPage page = repository.listTemplates(ctx, cursor);
				if (page.resourceTemplates()
					.stream()
					.anyMatch(template -> template.uriTemplate().equals(resourceReference.uri()))) {
					return true;
				}
				cursor = page.nextCursor();
			}
			while (cursor != null);
			return false;
		}
		return true;
	}

	@Bean
	@ConditionalOnMissingBean
	public McpServer mcpModernServer(@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties,
			ObjectProvider<McpSyncToolRepository> toolRepository,
			ObjectProvider<McpSyncResourceRepository> resourceRepository,
			ObjectProvider<McpSyncPromptRepository> promptRepository,
			ObjectProvider<McpSyncCompletionRepository> completionRepository, McpChangeFeed changeFeed,
			ObjectProvider<RequestStateCodec> requestStateCodec,
			ObjectProvider<McpModernServerCustomizer> customizers) {

		McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapper(jsonMapper);
		long listTtlMs = modernProperties.getListTtl().toMillis();
		CacheScope listCacheScope = modernProperties.getListCacheScope();

		McpServer.Builder builder = McpServer.builder()
			.serverInfo(Implementation.builder(serverProperties.getName(), serverProperties.getVersion()).build())
			.instructions(serverProperties.getInstructions())
			.supportedVersions(modernProperties.getSupportedVersions())
			.jsonMapper(mcpJsonMapper)
			.discoverCache(modernProperties.getDiscoverTtl().toMillis(), modernProperties.getDiscoverCacheScope());

		// A user-defined repository bean wins over the in-memory one; two
		// candidates of the same type fail fast with NoUniqueBeanDefinitionException.
		// The list cache hints are feature defaults, so they apply to any repository
		// that leaves a page's ttlMs/cacheScope null, custom ones included.
		toolRepository.ifAvailable(repository -> builder.feature(ToolsFeature.ofSync(repository, mcpJsonMapper,
				McpJsonDefaults.getSchemaValidator(), listTtlMs, listCacheScope)));
		resourceRepository.ifAvailable(repository -> builder
			.feature(ResourcesFeature.ofSync(repository, mcpJsonMapper, listTtlMs, listCacheScope)));
		promptRepository.ifAvailable(repository -> builder
			.feature(PromptsFeature.ofSync(repository, mcpJsonMapper, listTtlMs, listCacheScope)));
		completionRepository
			.ifAvailable(repository -> builder.feature(CompletionsFeature.ofSync(repository, mcpJsonMapper)));

		if (modernProperties.isSubscriptions()) {
			builder.subscriptions(changeFeed);
		}

		RequestStateCodec codec = requestStateCodec.getIfAvailable();
		builder.requestStateCodec(codec != null ? codec : requestStateCodec(modernProperties, mcpJsonMapper));

		customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "true")
	public StdioMcpTransport mcpModernStdioTransport(McpServer mcpServer,
			@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper) {
		return new StdioMcpTransport(mcpServer, new JacksonMcpJsonMapper(jsonMapper));
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "stdio", havingValue = "true")
	public StdioMcpTransportLifecycle mcpModernStdioTransportLifecycle(StdioMcpTransport stdioTransport) {
		return new StdioMcpTransportLifecycle(stdioTransport);
	}

	/**
	 * Builds the HMAC codec from the properties. Without a secret the SDK generates a
	 * random per-process key (and warns), so {@code request-state-ttl} is honoured either
	 * way.
	 */
	static RequestStateCodec requestStateCodec(McpServerModernProperties properties, McpJsonMapper jsonMapper) {
		HmacRequestStateCodec.Builder codec = HmacRequestStateCodec.builder().jsonMapper(jsonMapper);
		String secret = properties.getRequestStateSecret();
		if (secret != null && !secret.isBlank()) {
			codec.key(secret.getBytes(StandardCharsets.UTF_8));
		}
		if (properties.getRequestStateTtl() != null) {
			codec.ttl(properties.getRequestStateTtl());
		}
		return codec.build();
	}

	/**
	 * Starts the stdio transport with the application and closes it on shutdown.
	 */
	public static final class StdioMcpTransportLifecycle implements SmartLifecycle {

		private final StdioMcpTransport transport;

		private volatile boolean running;

		StdioMcpTransportLifecycle(StdioMcpTransport transport) {
			this.transport = transport;
		}

		@Override
		public void start() {
			if (!this.running) {
				this.running = true;
				this.transport.start().subscribe();
			}
		}

		@Override
		public void stop() {
			if (this.running) {
				this.running = false;
				this.transport.closeGracefully().block();
			}
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

	}

	/**
	 * Active when the server is enabled and the protocol is {@code MODERN}.
	 */
	public static class EnabledModernServerCondition extends AllNestedConditions {

		public EnabledModernServerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpServerEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "MODERN",
				matchIfMissing = false)
		static class ModernEnabledCondition {

		}

	}

}
