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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.modern.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.modern.McpSchema.ListToolsResult;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ModernListChangedHandlers}.
 *
 * @author Christian Tzolov
 */
class ModernListChangedHandlersTests {

	private static final McpModernConnectionInfo TICKETS = new McpModernConnectionInfo("tickets",
			Implementation.builder("test-client", "1").build());

	private static final McpModernConnectionInfo CATALOG = new McpModernConnectionInfo("catalog",
			Implementation.builder("test-client", "1").build());

	private static final Tool SEARCH = Tool.builder("search", Map.of("type", "object")).build();

	private static final Prompt SUMMARY = new Prompt("daily-summary", null, "Summarises the tickets", List.of(), null,
			null);

	private static final Resource ALL_TICKETS = Resource.builder("tickets://all", "tickets").build();

	private final McpSyncClient client = mock(McpSyncClient.class);

	@SuppressWarnings("unchecked")
	private final McpSyncClient.ListSpec<ListPromptsResult> promptsSpec = mock(McpSyncClient.ListSpec.class);

	@SuppressWarnings("unchecked")
	private final McpSyncClient.ListSpec<ListResourcesResult> resourcesSpec = mock(McpSyncClient.ListSpec.class);

	private final McpSyncClient.ListToolsSpec toolsSpec = mock(McpSyncClient.ListToolsSpec.class);

	/** How many clients were taken from the connection. */
	private final AtomicInteger clientsTaken = new AtomicInteger();

	private final SyncModernMcpConnection tickets = SyncModernMcpConnection.perRequest(TICKETS, () -> {
		this.clientsTaken.incrementAndGet();
		return this.client;
	});

	private DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

	@BeforeEach
	void stubTheListings() {
		when(this.client.listTools()).thenReturn(this.toolsSpec);
		when(this.toolsSpec.skipCache()).thenReturn(this.toolsSpec);
		when(this.toolsSpec.list()).thenReturn(ListToolsResult.builder(List.of(SEARCH)).build());

		when(this.client.prompts()).thenReturn(this.promptsSpec);
		when(this.promptsSpec.cursor(any())).thenReturn(this.promptsSpec);
		when(this.promptsSpec.list()).thenReturn(ListPromptsResult.builder(List.of(SUMMARY)).build());

		when(this.client.resources()).thenReturn(this.resourcesSpec);
		when(this.resourcesSpec.cursor(any())).thenReturn(this.resourcesSpec);
		when(this.resourcesSpec.list()).thenReturn(ListResourcesResult.builder(List.of(ALL_TICKETS)).build());
	}

	@Test
	void reportedChangeListsTheItemsAndCallsTheMethods() {
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);
		Handlers handlers = this.beanFactory.getBean(Handlers.class);

		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ToolsListChanged.class));
		assertThat(handlers.seen).containsExactly("tools [search]");

		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.PromptsListChanged.class));
		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ResourcesListChanged.class));
		assertThat(handlers.seen).containsExactly("tools [search]", "prompts [daily-summary]",
				"resources [tickets://all]");

		// A change of a single resource is not a change of the list.
		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ResourceUpdated.class));
		// Nor is the change of a connection that no method names.
		listChangedHandlers.changed(CATALOG, mock(SubscriptionEvent.ToolsListChanged.class));
		assertThat(handlers.seen).hasSize(3);
	}

	@Test
	void oneClientOfAConnectionServesAllItsChanges() {
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);

		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ToolsListChanged.class));
		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.PromptsListChanged.class));
		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ToolsListChanged.class));

		assertThat(this.clientsTaken).hasValue(1);
	}

	@Test
	void methodsAreCalledWhenAStreamIsEstablishedAgainButNotForTheFirstOne() {
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);
		Handlers handlers = this.beanFactory.getBean(Handlers.class);

		// The first stream: nothing was known about the server before it.
		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);
		assertThat(handlers.seen).isEmpty();

		// The stream ends. What the server does from now on is not heard.
		listChangedHandlers.changesMayBeMissed(TICKETS);
		assertThat(handlers.seen).isEmpty();

		// A stream is established again: the server may have changed in between.
		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);
		assertThat(handlers.seen).containsExactly("tools [search]", "prompts [daily-summary]",
				"resources [tickets://all]");

		// It ends once more, which again calls nothing.
		listChangedHandlers.changesMayBeMissed(TICKETS);
		assertThat(handlers.seen).hasSize(3);
	}

	@Test
	void failedListingAfterAStreamIsEstablishedAgainDoesNotKeepTheOtherMethodsFromBeingCalled() {
		when(this.toolsSpec.list()).thenThrow(new IllegalStateException("tools/list failed"));
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);
		Handlers handlers = this.beanFactory.getBean(Handlers.class);

		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);
		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);

		assertThat(handlers.seen).containsExactly("prompts [daily-summary]", "resources [tickets://all]");
	}

	@Test
	void pagesOfAListingAreReadUntilTheServerSendsNoCursor() {
		when(this.promptsSpec.list()).thenReturn(ListPromptsResult.builder(List.of(SUMMARY)).nextCursor("2").build(),
				ListPromptsResult.builder(List.of(SUMMARY)).nextCursor("3").build(),
				ListPromptsResult.builder(List.of(SUMMARY)).build());
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);
		Handlers handlers = this.beanFactory.getBean(Handlers.class);

		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.PromptsListChanged.class));

		assertThat(handlers.seen).containsExactly("prompts [daily-summary, daily-summary, daily-summary]");
	}

	@Test
	void listingThatNeverEndsIsGivenUp() {
		// A server that sends a cursor with every page.
		when(this.resourcesSpec.list())
			.thenReturn(ListResourcesResult.builder(List.of(ALL_TICKETS)).nextCursor("again").build());
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(Handlers.class);
		Handlers handlers = this.beanFactory.getBean(Handlers.class);

		assertThatIllegalStateException()
			.isThrownBy(() -> listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.ResourcesListChanged.class)))
			.withMessageContaining("did not end within 100 pages");
		assertThat(handlers.seen).isEmpty();
	}

	@Test
	void onlyTheListsThatAMethodWantsAreListed() {
		ModernListChangedHandlers listChangedHandlers = this.listChangedHandlers(ToolHandlersOnly.class);

		listChangedHandlers.changed(TICKETS, mock(SubscriptionEvent.PromptsListChanged.class));
		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);
		listChangedHandlers.streamEstablished(TICKETS);
		listChangedHandlers.changesMayBeMissed(TICKETS);

		assertThat(this.beanFactory.getBean(ToolHandlersOnly.class).seen).containsExactly("tools [search]");
		verifyNoInteractions(this.promptsSpec, this.resourcesSpec);
	}

	@Test
	void thereIsNoListenerIfNoMethodCanBeCalledForAListeningConnection() {
		// No list-changed method at all.
		assertThat(ModernListChangedHandlers.forConnections(List.of(this.tickets), this.registry(NoHandlers.class)))
			.isNull();
		// The methods name another connection than the one that listens.
		assertThat(ModernListChangedHandlers.forConnections(
				List.of(SyncModernMcpConnection.perRequest(CATALOG, () -> this.client)), this.registry(Handlers.class)))
			.isNull();
		// A reactive method is not called by the modern client.
		assertThat(
				ModernListChangedHandlers.forConnections(List.of(this.tickets), this.registry(ReactiveHandlers.class)))
			.isNull();
		// No connection listens.
		assertThat(ModernListChangedHandlers.forConnections(List.of(), this.registry(Handlers.class))).isNull();
	}

	private ModernListChangedHandlers listChangedHandlers(Class<?> handlersClass) {
		ModernListChangedHandlers listChangedHandlers = ModernListChangedHandlers.forConnections(List.of(this.tickets),
				this.registry(handlersClass));
		assertThat(listChangedHandlers).isNotNull();
		return listChangedHandlers;
	}

	/**
	 * A registry that knows the methods of the given class only.
	 */
	private ClientMcpSyncHandlersRegistry registry(Class<?> handlersClass) {
		this.beanFactory = new DefaultListableBeanFactory();
		this.beanFactory.registerBeanDefinition("handlers",
				BeanDefinitionBuilder.genericBeanDefinition(handlersClass).getBeanDefinition());
		var registry = new ClientMcpSyncHandlersRegistry();
		registry.postProcessBeanFactory(this.beanFactory);
		registry.afterSingletonsInstantiated();
		return registry;
	}

	static class Handlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpToolListChanged(clients = "tickets")
		void tools(List<Tool> tools) {
			this.seen.add("tools " + tools.stream().map(Tool::name).toList());
		}

		@McpPromptListChanged(clients = "tickets")
		void prompts(List<Prompt> prompts) {
			this.seen.add("prompts " + prompts.stream().map(Prompt::name).toList());
		}

		@McpResourceListChanged(clients = "tickets")
		void resources(List<Resource> resources) {
			this.seen.add("resources " + resources.stream().map(Resource::uri).toList());
		}

	}

	static class ToolHandlersOnly {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpToolListChanged(clients = "tickets")
		void tools(List<Tool> tools) {
			this.seen.add("tools " + tools.stream().map(Tool::name).toList());
		}

	}

	static class ReactiveHandlers {

		@McpToolListChanged(clients = "tickets")
		Mono<Void> tools(List<io.modelcontextprotocol.spec.McpSchema.Tool> tools) {
			return Mono.empty();
		}

	}

	static class NoHandlers {

	}

}
