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
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.server.HmacRequestStateCodec;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.feature.McpAsyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpAsyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.McpAsyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.modern.server.feature.McpAsyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.PromptsFeature;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.customizer.McpModernServerCustomizer;
import org.springframework.ai.mcp.modern.AsyncResourceRegistration;
import org.springframework.ai.mcp.modern.AsyncResourceTemplateRegistration;
import org.springframework.ai.mcp.modern.InMemoryAsyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemoryAsyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemoryAsyncToolRepository;
import org.springframework.ai.mcp.modern.InMemorySyncPromptRepository;
import org.springframework.ai.mcp.modern.InMemorySyncResourceRepository;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.SyncResourceRegistration;
import org.springframework.ai.mcp.modern.SyncResourceTemplateRegistration;
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
 * {@link McpSyncResourceRepository} and {@link McpSyncPromptRepository}, or their
 * {@code McpAsync*Repository} twins. A user-defined repository bean of either flavour
 * takes precedence; otherwise an in-memory repository is created and fed by every
 * {@code List<McpSyncTool>}, {@code List<SyncResourceRegistration>},
 * {@code List<SyncResourceTemplateRegistration>} and {@code List<McpSyncPrompt>} bean
 * (or, for {@code spring.ai.mcp.server.type=ASYNC}, the {@code McpAsync*} and
 * {@code Async*Registration} lists), which is how annotated beans and
 * {@code ToolCallback}s reach the server. Completions travel with their prompt or
 * resource template, so the prompt and resource repositories serve them too;
 * {@code spring.ai.mcp.server.capabilities.completion=false} turns them off. The
 * in-memory repositories are beans themselves, so tools can be added or removed at
 * runtime; changes are broadcast to {@code subscriptions/listen} clients through the
 * {@link McpChangeFeed} bean. An application that wants to filter or decorate a
 * repository per request defines its own repository bean, typically one that builds the
 * in-memory repository from the same list beans and wraps it.
 * <p>
 * The {@code type} property only picks the in-memory defaults and the annotation
 * providers: the server accepts a repository bean of either flavour for each primitive,
 * and fails at startup when both are defined for the same one.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration(after = McpServerJsonMapperAutoConfiguration.class)
@ConditionalOnClass(McpServer.class)
@EnableConfigurationProperties({ McpServerProperties.class, McpServerModernProperties.class })
@Conditional(McpServerModernAutoConfiguration.EnabledModernServerCondition.class)
public class McpServerModernAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpServerModernAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public McpChangeFeed mcpChangeFeed() {
		return new McpChangeFeed();
	}

	@Bean
	@ConditionalOnMissingBean({ McpSyncToolRepository.class, McpAsyncToolRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.tool", havingValue = "true",
			matchIfMissing = true)
	public InMemorySyncToolRepository mcpModernToolRepository(ObjectProvider<List<McpSyncTool>> tools,
			ObjectProvider<List<McpAsyncTool>> asyncTools, McpChangeFeed changeFeed,
			McpServerModernProperties modernProperties) {
		rejectUnservedLists(asyncTools, "List<McpAsyncTool>", "tool", "SYNC", "ASYNC", "McpAsyncToolRepository");
		List<McpSyncTool> all = tools.stream().flatMap(List::stream).toList();
		logger.info("Registered modern tools: " + all.size());
		return InMemorySyncToolRepository.builder()
			.tools(all)
			.changeBroadcaster(changeFeed)
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean({ McpSyncResourceRepository.class, McpAsyncResourceRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.resource",
			havingValue = "true", matchIfMissing = true)
	public InMemorySyncResourceRepository mcpModernResourceRepository(
			ObjectProvider<List<SyncResourceRegistration>> resources,
			ObjectProvider<List<SyncResourceTemplateRegistration>> resourceTemplates,
			ObjectProvider<List<AsyncResourceRegistration>> asyncResources,
			ObjectProvider<List<AsyncResourceTemplateRegistration>> asyncResourceTemplates, McpChangeFeed changeFeed,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties) {
		rejectUnservedLists(asyncResources, "List<AsyncResourceRegistration>", "resource", "SYNC", "ASYNC",
				"McpAsyncResourceRepository");
		rejectUnservedLists(asyncResourceTemplates, "List<AsyncResourceTemplateRegistration>", "resource", "SYNC",
				"ASYNC", "McpAsyncResourceRepository");
		List<SyncResourceRegistration> allResources = resources.stream().flatMap(List::stream).toList();
		List<SyncResourceTemplateRegistration> allTemplates = resourceTemplates.stream().flatMap(List::stream).toList();
		logger.info(
				"Registered modern resources: " + allResources.size() + ", resource templates: " + allTemplates.size());
		return InMemorySyncResourceRepository.builder()
			.resources(allResources)
			.resourceTemplates(allTemplates)
			.changeBroadcaster(changeFeed)
			.subscribeSupported(modernProperties.isResourceSubscribe())
			.completionSupported(serverProperties.getCapabilities().isCompletion())
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean({ McpSyncPromptRepository.class, McpAsyncPromptRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.prompt",
			havingValue = "true", matchIfMissing = true)
	public InMemorySyncPromptRepository mcpModernPromptRepository(ObjectProvider<List<McpSyncPrompt>> prompts,
			ObjectProvider<List<McpAsyncPrompt>> asyncPrompts, McpChangeFeed changeFeed,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties) {
		rejectUnservedLists(asyncPrompts, "List<McpAsyncPrompt>", "prompt", "SYNC", "ASYNC",
				"McpAsyncPromptRepository");
		List<McpSyncPrompt> all = prompts.stream().flatMap(List::stream).toList();
		logger.info("Registered modern prompts: " + all.size());
		return InMemorySyncPromptRepository.builder()
			.prompts(all)
			.changeBroadcaster(changeFeed)
			.completionSupported(serverProperties.getCapabilities().isCompletion())
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	// The asynchronous twins, active for spring.ai.mcp.server.type=ASYNC and fed by the
	// McpAsync* and Async*Registration list beans.

	@Bean
	@ConditionalOnMissingBean({ McpSyncToolRepository.class, McpAsyncToolRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.tool", havingValue = "true",
			matchIfMissing = true)
	public InMemoryAsyncToolRepository mcpModernAsyncToolRepository(ObjectProvider<List<McpAsyncTool>> tools,
			ObjectProvider<List<McpSyncTool>> syncTools, McpChangeFeed changeFeed,
			McpServerModernProperties modernProperties) {
		rejectUnservedLists(syncTools, "List<McpSyncTool>", "tool", "ASYNC", "SYNC", "McpSyncToolRepository");
		List<McpAsyncTool> all = tools.stream().flatMap(List::stream).toList();
		logger.info("Registered modern async tools: " + all.size());
		return InMemoryAsyncToolRepository.builder()
			.tools(all)
			.changeBroadcaster(changeFeed)
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean({ McpSyncResourceRepository.class, McpAsyncResourceRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.resource",
			havingValue = "true", matchIfMissing = true)
	public InMemoryAsyncResourceRepository mcpModernAsyncResourceRepository(
			ObjectProvider<List<AsyncResourceRegistration>> resources,
			ObjectProvider<List<AsyncResourceTemplateRegistration>> resourceTemplates,
			ObjectProvider<List<SyncResourceRegistration>> syncResources,
			ObjectProvider<List<SyncResourceTemplateRegistration>> syncResourceTemplates, McpChangeFeed changeFeed,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties) {
		rejectUnservedLists(syncResources, "List<SyncResourceRegistration>", "resource", "ASYNC", "SYNC",
				"McpSyncResourceRepository");
		rejectUnservedLists(syncResourceTemplates, "List<SyncResourceTemplateRegistration>", "resource", "ASYNC",
				"SYNC", "McpSyncResourceRepository");
		List<AsyncResourceRegistration> allResources = resources.stream().flatMap(List::stream).toList();
		List<AsyncResourceTemplateRegistration> allTemplates = resourceTemplates.stream()
			.flatMap(List::stream)
			.toList();
		logger.info("Registered modern async resources: " + allResources.size() + ", resource templates: "
				+ allTemplates.size());
		return InMemoryAsyncResourceRepository.builder()
			.resources(allResources)
			.resourceTemplates(allTemplates)
			.changeBroadcaster(changeFeed)
			.subscribeSupported(modernProperties.isResourceSubscribe())
			.completionSupported(serverProperties.getCapabilities().isCompletion())
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean({ McpSyncPromptRepository.class, McpAsyncPromptRepository.class })
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "capabilities.prompt",
			havingValue = "true", matchIfMissing = true)
	public InMemoryAsyncPromptRepository mcpModernAsyncPromptRepository(ObjectProvider<List<McpAsyncPrompt>> prompts,
			ObjectProvider<List<McpSyncPrompt>> syncPrompts, McpChangeFeed changeFeed,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties) {
		rejectUnservedLists(syncPrompts, "List<McpSyncPrompt>", "prompt", "ASYNC", "SYNC", "McpSyncPromptRepository");
		List<McpAsyncPrompt> all = prompts.stream().flatMap(List::stream).toList();
		logger.info("Registered modern async prompts: " + all.size());
		return InMemoryAsyncPromptRepository.builder()
			.prompts(all)
			.changeBroadcaster(changeFeed)
			.completionSupported(serverProperties.getCapabilities().isCompletion())
			.pageSize(modernProperties.getPageSize())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public McpServer mcpModernServer(@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
			McpServerProperties serverProperties, McpServerModernProperties modernProperties,
			ObjectProvider<McpSyncToolRepository> toolRepository,
			ObjectProvider<McpAsyncToolRepository> asyncToolRepository,
			ObjectProvider<McpSyncResourceRepository> resourceRepository,
			ObjectProvider<McpAsyncResourceRepository> asyncResourceRepository,
			ObjectProvider<McpSyncPromptRepository> promptRepository,
			ObjectProvider<McpAsyncPromptRepository> asyncPromptRepository, McpChangeFeed changeFeed,
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

		// A user-defined repository bean wins over the in-memory one; two candidates of
		// the same type fail fast with NoUniqueBeanDefinitionException, and a sync next
		// to an async one for the same primitive fails fast below. The list cache hints
		// are feature defaults, so they apply to any repository that leaves a page's
		// ttlMs/cacheScope null, custom ones included.
		McpSyncToolRepository tools = one(toolRepository, asyncToolRepository, McpSyncToolRepository.class,
				McpAsyncToolRepository.class);
		if (tools != null) {
			builder.feature(ToolsFeature.ofSync(tools, mcpJsonMapper, McpJsonDefaults.getSchemaValidator(), listTtlMs,
					listCacheScope));
		}
		asyncToolRepository.ifAvailable(repository -> builder.feature(ToolsFeature.ofAsync(repository, mcpJsonMapper,
				McpJsonDefaults.getSchemaValidator(), listTtlMs, listCacheScope)));
		McpSyncResourceRepository resources = one(resourceRepository, asyncResourceRepository,
				McpSyncResourceRepository.class, McpAsyncResourceRepository.class);
		if (resources != null) {
			builder.feature(ResourcesFeature.ofSync(resources, mcpJsonMapper, listTtlMs, listCacheScope));
		}
		asyncResourceRepository.ifAvailable(repository -> builder
			.feature(ResourcesFeature.ofAsync(repository, mcpJsonMapper, listTtlMs, listCacheScope)));
		McpSyncPromptRepository prompts = one(promptRepository, asyncPromptRepository, McpSyncPromptRepository.class,
				McpAsyncPromptRepository.class);
		if (prompts != null) {
			builder.feature(PromptsFeature.ofSync(prompts, mcpJsonMapper, listTtlMs, listCacheScope));
		}
		asyncPromptRepository.ifAvailable(repository -> builder
			.feature(PromptsFeature.ofAsync(repository, mcpJsonMapper, listTtlMs, listCacheScope)));

		if (modernProperties.isSubscriptions()) {
			builder.subscriptions(changeFeed);
		}

		RequestStateCodec codec = requestStateCodec.getIfAvailable();
		builder.requestStateCodec(codec != null ? codec : requestStateCodec(modernProperties, mcpJsonMapper));

		customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
		return builder.build();
	}

	/**
	 * Fails when list beans of the other flavour hold primitives that the in-memory
	 * repository would not serve: the in-memory repository of each type reads only the
	 * lists of that type, and it exists only when no user repository serves the
	 * primitive, so nothing would consume them and they would silently be missing.
	 */
	private static void rejectUnservedLists(ObjectProvider<? extends List<?>> lists, String listType, String primitive,
			String type, String otherType, String otherRepositoryType) {
		int count = lists.stream().mapToInt(List::size).sum();
		if (count > 0) {
			throw new IllegalStateException(
					listType + " beans are defined (" + count + (count == 1 ? " entry" : " entries")
							+ "), but with spring.ai.mcp.server.type=" + type + " the in-memory " + primitive
							+ " repository serves only the " + type + " list beans. Set spring.ai.mcp.server.type="
							+ otherType + ", or define a " + otherRepositoryType + " bean that serves them.");
		}
	}

	/**
	 * The sync repository of a primitive, or {@code null} when none is defined; fails
	 * fast when an async one is defined next to it, because a primitive is served by one
	 * repository.
	 */
	private static <S, A> @Nullable S one(ObjectProvider<S> sync, ObjectProvider<A> async, Class<S> syncType,
			Class<A> asyncType) {
		S repository = sync.getIfAvailable();
		if (repository != null && async.getIfAvailable() != null) {
			throw new IllegalStateException("Both a " + syncType.getSimpleName() + " and a " + asyncType.getSimpleName()
					+ " bean are defined; a modern server serves each primitive from one repository, sync or async");
		}
		return repository;
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

	/** The smallest HMAC-SHA256 key the auto-configuration accepts, in bytes. */
	static final int MIN_REQUEST_STATE_SECRET_BYTES = 32;

	/**
	 * Builds the HMAC codec from the properties. Without a secret the SDK generates a
	 * random per-process key (and warns), so {@code request-state-ttl} is honoured either
	 * way.
	 */
	static RequestStateCodec requestStateCodec(McpServerModernProperties properties, McpJsonMapper jsonMapper) {
		HmacRequestStateCodec.Builder codec = HmacRequestStateCodec.builder().jsonMapper(jsonMapper);
		String secret = properties.getRequestStateSecret();
		if (secret != null && !secret.isBlank()) {
			byte[] key = secret.getBytes(StandardCharsets.UTF_8);
			Assert.isTrue(key.length >= MIN_REQUEST_STATE_SECRET_BYTES,
					() -> McpServerModernProperties.CONFIG_PREFIX + ".request-state-secret must be at least "
							+ MIN_REQUEST_STATE_SECRET_BYTES + " bytes; it is " + key.length);
			codec.key(key);
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
