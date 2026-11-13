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

package org.springframework.ai.mcp.annotation.spring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the handlers that {@link ClientMcpAsyncHandlersRegistry} provides to the
 * clients of the 2026-07-28 ("modern") MCP revision.
 *
 * @author Christian Tzolov
 */
class ClientMcpAsyncHandlersRegistryModernTests {

	private static final Map<String, Object> SCHEMA = Map.of("type", "object");

	private static final List<Tool> TOOLS = List.of(Tool.builder("search", SCHEMA).build());

	private static final List<Prompt> PROMPTS = List
		.of(new Prompt("daily-summary", null, "Summarises the tickets", List.of(), null, null));

	private static final List<Resource> RESOURCES = List.of(Resource.builder("tickets://all", "tickets").build());

	@Test
	void whatAModernClientCanCallIsKnownBeforeTheHandlersAreResolved() {
		ClientMcpAsyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory(), Handlers.class);

		assertThat(registry.handlesModernFormElicitation("legacy-typed")).isTrue();
		assertThat(registry.handlesModernUrlElicitation("legacy-typed")).isFalse();

		assertThat(registry.handlesModernFormElicitation("modern-typed")).isTrue();
		assertThat(registry.handlesModernUrlElicitation("modern-typed")).isTrue();

		// A legacy URL request needs an elicitationId, which a modern request lacks.
		assertThat(registry.handlesModernFormElicitation("legacy-url")).isFalse();
		assertThat(registry.handlesModernUrlElicitation("legacy-url")).isFalse();

		assertThat(registry.handlesModernProgress("progress")).isTrue();
		assertThat(registry.handlesModernProgress("unknown")).isFalse();
	}

	@Test
	void synchronousMethodsDoNotServeTheClientsOfTheAsynchronousRegistry() {
		ClientMcpAsyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory(),
				SynchronousHandlers.class);

		// The methods name the client, but the asynchronous client cannot call them.
		assertThat(registry.hasHandlers("sync", McpElicitation.class)).isTrue();
		assertThat(registry.handlesModernFormElicitation("sync")).isFalse();
		assertThat(registry.hasHandlers("sync", McpProgress.class)).isTrue();
		assertThat(registry.handlesModernProgress("sync")).isFalse();
		assertThat(registry.hasHandlers("sync", McpToolListChanged.class)).isTrue();
		assertThat(registry.handlesModernListChanged("sync", McpToolListChanged.class)).isFalse();
	}

	@Test
	void reactiveListChangedMethodsAreKnownBeforeTheHandlersAreResolved() {
		ClientMcpAsyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory(),
				ListChangedHandlers.class);

		assertThat(registry.handlesModernListChanged("lists", McpToolListChanged.class)).isTrue();
		assertThat(registry.handlesModernListChanged("lists", McpPromptListChanged.class)).isTrue();
		assertThat(registry.handlesModernListChanged("lists", McpResourceListChanged.class)).isTrue();
		assertThat(registry.handlesModernListChanged("other", McpToolListChanged.class)).isFalse();
		// Not a list-changed annotation.
		assertThat(registry.handlesModernListChanged("lists", McpProgress.class)).isFalse();
	}

	@Test
	void listChangedMethodsOfBothSchemasHearAboutTheChangesOfAModernClient() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpAsyncHandlersRegistry registry = resolved(beanFactory, ListChangedHandlers.class);
		ListChangedHandlers handlers = beanFactory.getBean(ListChangedHandlers.class);

		StepVerifier
			.create(registry.handleModernToolListChanged("lists", TOOLS)
				.then(registry.handleModernPromptListChanged("lists", PROMPTS))
				.then(registry.handleModernResourceListChanged("lists", RESOURCES)))
			.verifyComplete();

		assertThat(handlers.seen).containsExactlyInAnyOrder("legacy tools [search]", "modern tools [search]",
				"legacy prompts [daily-summary]", "modern prompts [daily-summary]", "legacy resources [tickets://all]",
				"modern resources [tickets://all]");
	}

	@Test
	void listChangedMethodsOfBothSchemasHearAboutTheChangesOfALegacyClient() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpAsyncHandlersRegistry registry = resolved(beanFactory, ListChangedHandlers.class);
		ListChangedHandlers handlers = beanFactory.getBean(ListChangedHandlers.class);

		StepVerifier
			.create(registry.handleToolListChanged("lists", ModernClientHandlerSupport.toLegacyTools(TOOLS))
				.then(registry.handlePromptListChanged("lists", ModernClientHandlerSupport.toLegacyPrompts(PROMPTS)))
				.then(registry.handleResourceListChanged("lists",
						ModernClientHandlerSupport.toLegacyResources(RESOURCES))))
			.verifyComplete();

		assertThat(handlers.seen).containsExactlyInAnyOrder("legacy tools [search]", "modern tools [search]",
				"legacy prompts [daily-summary]", "modern prompts [daily-summary]", "legacy resources [tickets://all]",
				"modern resources [tickets://all]");
	}

	@Test
	void failingListChangedMethodDoesNotKeepTheOthersFromHearingTheChange() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpAsyncHandlersRegistry registry = resolved(beanFactory, FailingListChangedHandlers.class);
		FailingListChangedHandlers handlers = beanFactory.getBean(FailingListChangedHandlers.class);

		StepVerifier
			.create(registry.handleModernToolListChanged("lists", TOOLS)
				.then(registry.handleModernPromptListChanged("lists", PROMPTS))
				.then(registry.handleModernResourceListChanged("lists", RESOURCES)))
			.verifyComplete();

		assertThat(handlers.seen).containsExactlyInAnyOrder("tools [search]", "prompts [daily-summary]",
				"resources [tickets://all]");
	}

	@Test
	void listChangeOfAClientWithoutAMethodIsIgnored() {
		ClientMcpAsyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), ListChangedHandlers.class);

		StepVerifier
			.create(registry.handleModernToolListChanged("unknown", TOOLS)
				.then(registry.handleModernPromptListChanged("unknown", PROMPTS))
				.then(registry.handleModernResourceListChanged("unknown", RESOURCES)))
			.verifyComplete();
	}

	@Test
	void methodWithLegacyTypesAnswersAModernFormRequest() {
		ClientMcpAsyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), Handlers.class);

		StepVerifier
			.create(registry.handleModernElicitation("legacy-typed",
					new ElicitFormRequest("Delete the ticket?", SCHEMA, null)))
			.assertNext(result -> {
				assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);
				assertThat(result.content()).containsEntry("confirmed", true);
			})
			.verifyComplete();
	}

	@Test
	void methodWithModernTypesAnswersModernFormAndUrlRequestsAndALegacyRequest() {
		ClientMcpAsyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), Handlers.class);

		StepVerifier
			.create(registry.handleModernElicitation("modern-typed", new ElicitFormRequest("Delete?", SCHEMA, null)))
			.assertNext(result -> assertThat(result.content()).containsEntry("asked", "Delete?"))
			.verifyComplete();
		StepVerifier
			.create(registry.handleModernElicitation("modern-typed",
					new ElicitUrlRequest("Log in", "https://example.com/login", null)))
			.assertNext(result -> assertThat(result.content()).containsEntry("asked", "Log in"))
			.verifyComplete();

		// The same method answers a legacy client of that name.
		StepVerifier.create(registry.handleElicitation("modern-typed",
				new McpSchema.ElicitFormRequest("Delete the legacy ticket?", SCHEMA, null)))
			.assertNext(result -> {
				assertThat(result.action()).isEqualTo(McpSchema.ElicitResult.Action.ACCEPT);
				assertThat(result.content()).containsEntry("asked", "Delete the legacy ticket?");
			})
			.verifyComplete();
	}

	@Test
	void requestThatNoMethodAnswersFails() {
		ClientMcpAsyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), Handlers.class);

		StepVerifier
			.create(registry.handleModernElicitation("legacy-typed",
					new ElicitUrlRequest("Log in", "https://example.com/login", null)))
			.verifyErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("URL")
				.hasMessageContaining("legacy-typed"));
		StepVerifier.create(registry.handleModernElicitation("unknown", new ElicitFormRequest("Delete?", SCHEMA, null)))
			.verifyError(IllegalStateException.class);
	}

	@Test
	void elicitationAndProgressMethodsReceiveTheToolContextOfTheToolCall() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpAsyncHandlersRegistry registry = resolved(beanFactory, ToolContextHandlers.class);
		ToolContextHandlers handlers = beanFactory.getBean(ToolContextHandlers.class);
		ToolContext toolContext = new ToolContext(Map.of("user", "alice"));

		StepVerifier
			.create(registry.handleModernElicitation("tc", new ElicitFormRequest("Delete?", SCHEMA, null), toolContext))
			.assertNext(result -> assertThat(result.content()).containsEntry("user", "alice"))
			.verifyComplete();
		// Outside of a tool call there is no tool context.
		StepVerifier.create(registry.handleModernElicitation("tc", new ElicitFormRequest("Delete?", SCHEMA, null)))
			.assertNext(result -> assertThat(result.content()).containsEntry("user", "nobody"))
			.verifyComplete();

		StepVerifier
			.create(registry.handleModernProgress("tc", new ProgressParams(7L, 0.5, 1.0, "half", null), toolContext))
			.verifyComplete();
		assertThat(handlers.seen).containsExactly("progress 0.5 for alice");
	}

	@Test
	void progressReachesTheMethodsOfBothSchemasOnBothProtocols() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpAsyncHandlersRegistry registry = resolved(beanFactory, Handlers.class);
		Handlers handlers = beanFactory.getBean(Handlers.class);

		StepVerifier.create(registry.handleModernProgress("progress", new ProgressParams(7L, 0.5, 1.0, "half", null)))
			.verifyComplete();
		assertThat(handlers.seen).containsExactlyInAnyOrder("legacy 0.5 half", "modern 0.5 half");

		handlers.seen.clear();
		StepVerifier
			.create(registry.handleProgress("progress", new McpSchema.ProgressNotification("7", 0.25, 1.0, "quarter")))
			.verifyComplete();
		assertThat(handlers.seen).containsExactlyInAnyOrder("legacy 0.25 quarter", "modern 0.25 quarter");

		// A client without a handler is ignored.
		StepVerifier.create(registry.handleModernProgress("unknown", new ProgressParams(7L, 0.5, 1.0, "half", null)))
			.verifyComplete();
	}

	private static ClientMcpAsyncHandlersRegistry resolved(DefaultListableBeanFactory beanFactory,
			Class<?> handlersClass) {
		ClientMcpAsyncHandlersRegistry registry = postProcessed(beanFactory, handlersClass);
		registry.afterSingletonsInstantiated();
		return registry;
	}

	private static ClientMcpAsyncHandlersRegistry postProcessed(DefaultListableBeanFactory beanFactory,
			Class<?> handlersClass) {
		var registry = new ClientMcpAsyncHandlersRegistry();
		beanFactory.registerBeanDefinition("handlers",
				BeanDefinitionBuilder.genericBeanDefinition(handlersClass).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		return registry;
	}

	record Confirmation(boolean confirmed) {
	}

	static class Handlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = "legacy-typed")
		Mono<StructuredElicitResult<Confirmation>> legacyTyped(McpSchema.ElicitRequest request) {
			return Mono
				.just(new StructuredElicitResult<>(McpSchema.ElicitResult.Action.ACCEPT, new Confirmation(true), null));
		}

		@McpElicitation(clients = "modern-typed")
		Mono<ElicitResult> modernTyped(ElicitRequest request) {
			return Mono.just(ElicitResult.accept(Map.of("asked", request.message())));
		}

		@McpElicitation(clients = "legacy-url")
		Mono<McpSchema.ElicitResult> legacyUrl(McpSchema.ElicitUrlRequest request) {
			return Mono.just(McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT).build());
		}

		@McpProgress(clients = "progress")
		Mono<Void> legacyProgress(McpSchema.ProgressNotification notification) {
			this.seen.add("legacy " + notification.progress() + " " + notification.message());
			return Mono.empty();
		}

		@McpProgress(clients = "progress")
		Mono<Void> modernProgress(ProgressParams progress) {
			this.seen.add("modern " + progress.progress() + " " + progress.message());
			return Mono.empty();
		}

	}

	/** Handler methods that take the tool context of the tool call they serve. */
	static class ToolContextHandlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = "tc")
		Mono<ElicitResult> elicitation(ElicitFormRequest request, ToolContext toolContext) {
			Object user = toolContext != null ? toolContext.getContext().get("user") : "nobody";
			return Mono.just(ElicitResult.accept(Map.of("user", user)));
		}

		@McpProgress(clients = "tc")
		Mono<Void> progress(ToolContext toolContext, ProgressParams progress) {
			this.seen.add("progress " + progress.progress() + " for " + toolContext.getContext().get("user"));
			return Mono.empty();
		}

	}

	/** Synchronous methods, which an asynchronous client does not call. */
	static class SynchronousHandlers {

		@McpElicitation(clients = "sync")
		ElicitResult elicitation(ElicitFormRequest request) {
			return ElicitResult.decline();
		}

		@McpProgress(clients = "sync")
		void progress(ProgressParams progress) {
		}

		@McpToolListChanged(clients = "sync")
		void tools(List<Tool> tools) {
		}

	}

	/**
	 * Reactive list-changed methods that take the lists of the legacy and of the modern
	 * schema.
	 */
	static class ListChangedHandlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpToolListChanged(clients = "lists")
		Mono<Void> legacyTools(List<McpSchema.Tool> tools) {
			return Mono
				.fromRunnable(() -> this.seen.add("legacy tools " + tools.stream().map(McpSchema.Tool::name).toList()));
		}

		@McpToolListChanged(clients = "lists")
		Mono<Void> modernTools(List<Tool> tools) {
			return Mono.fromRunnable(() -> this.seen.add("modern tools " + tools.stream().map(Tool::name).toList()));
		}

		@McpPromptListChanged(clients = "lists")
		Mono<Void> legacyPrompts(List<McpSchema.Prompt> prompts) {
			return Mono.fromRunnable(
					() -> this.seen.add("legacy prompts " + prompts.stream().map(McpSchema.Prompt::name).toList()));
		}

		@McpPromptListChanged(clients = "lists")
		Mono<Void> modernPrompts(List<Prompt> prompts) {
			return Mono
				.fromRunnable(() -> this.seen.add("modern prompts " + prompts.stream().map(Prompt::name).toList()));
		}

		@McpResourceListChanged(clients = "lists")
		Mono<Void> legacyResources(List<McpSchema.Resource> resources) {
			return Mono.fromRunnable(() -> this.seen
				.add("legacy resources " + resources.stream().map(McpSchema.Resource::uri).toList()));
		}

		@McpResourceListChanged(clients = "lists")
		Mono<Void> modernResources(List<Resource> resources) {
			return Mono.fromRunnable(
					() -> this.seen.add("modern resources " + resources.stream().map(Resource::uri).toList()));
		}

	}

	/**
	 * For each kind of list a method that fails when it is called, one whose {@code Mono}
	 * fails, and one that does not fail.
	 */
	static class FailingListChangedHandlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpToolListChanged(clients = "lists")
		Mono<Void> failingTools(List<Tool> tools) {
			throw new IllegalStateException("tools");
		}

		@McpToolListChanged(clients = "lists")
		Mono<Void> failingToolsLater(List<Tool> tools) {
			return Mono.error(new IllegalStateException("tools, later"));
		}

		@McpToolListChanged(clients = "lists")
		Mono<Void> tools(List<Tool> tools) {
			return Mono.fromRunnable(() -> this.seen.add("tools " + tools.stream().map(Tool::name).toList()));
		}

		@McpPromptListChanged(clients = "lists")
		Mono<Void> failingPrompts(List<Prompt> prompts) {
			return Mono.error(new IllegalStateException("prompts"));
		}

		@McpPromptListChanged(clients = "lists")
		Mono<Void> prompts(List<Prompt> prompts) {
			return Mono.fromRunnable(() -> this.seen.add("prompts " + prompts.stream().map(Prompt::name).toList()));
		}

		@McpResourceListChanged(clients = "lists")
		Mono<Void> failingResources(List<Resource> resources) {
			return Mono.error(new IllegalStateException("resources"));
		}

		@McpResourceListChanged(clients = "lists")
		Mono<Void> resources(List<Resource> resources) {
			return Mono
				.fromRunnable(() -> this.seen.add("resources " + resources.stream().map(Resource::uri).toList()));
		}

	}

}
