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
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the handlers that {@link ClientMcpSyncHandlersRegistry} offers to the clients
 * of the 2026-07-28 ("modern") MCP revision. {@code McpSchema} is the schema of the
 * legacy protocol; the types imported by their simple name are those of the modern
 * revision.
 *
 * @author Christian Tzolov
 */
class ClientMcpSyncHandlersRegistryModernTests {

	private static final Map<String, Object> SCHEMA = Map.of("type", "object");

	@Test
	void elicitationModesAreKnownBeforeTheHandlersAreResolved() {
		ClientMcpSyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory());

		assertThat(registry.handlesModernFormElicitation("legacy-typed")).isTrue();
		assertThat(registry.handlesModernUrlElicitation("legacy-typed")).isFalse();

		assertThat(registry.handlesModernFormElicitation("modern-typed")).isTrue();
		assertThat(registry.handlesModernUrlElicitation("modern-typed")).isTrue();

		assertThat(registry.handlesModernFormElicitation("modern-url")).isFalse();
		assertThat(registry.handlesModernUrlElicitation("modern-url")).isTrue();

		// A legacy URL request needs an elicitationId, which a modern request lacks.
		assertThat(registry.handlesModernFormElicitation("legacy-url")).isFalse();
		assertThat(registry.handlesModernUrlElicitation("legacy-url")).isFalse();

		assertThat(registry.handlesModernFormElicitation("unknown")).isFalse();
		assertThat(registry.handlesModernUrlElicitation("unknown")).isFalse();
	}

	@Test
	void handlerAnnotationsOfAClientAreKnownBeforeTheHandlersAreResolved() {
		ClientMcpSyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory());

		assertThat(registry.hasHandlers("progress", McpProgress.class)).isTrue();
		assertThat(registry.hasHandlers("progress", McpElicitation.class)).isFalse();
		assertThat(registry.hasHandlers("legacy-typed", McpElicitation.class)).isTrue();
		assertThat(registry.hasHandlers("other", McpSampling.class)).isTrue();
		assertThat(registry.hasHandlers("other", McpLogging.class)).isTrue();
		assertThat(registry.hasHandlers("unknown", McpProgress.class)).isFalse();
	}

	@Test
	void methodWithLegacyTypesAnswersAModernFormRequest() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());

		ElicitResult result = registry.handleModernElicitation("legacy-typed",
				new ElicitFormRequest("Delete the ticket?", SCHEMA, null));

		assertThat(result).isEqualTo(ElicitResult.accept(Map.of("asked", "Delete the ticket?", "by", "legacyTyped")));
	}

	@Test
	void methodWithModernTypesAnswersALegacyRequest() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());

		McpSchema.ElicitResult result = registry.handleElicitation("modern-typed",
				new McpSchema.ElicitFormRequest("Delete the ticket?", SCHEMA, null));

		assertThat(result.action()).isEqualTo(McpSchema.ElicitResult.Action.ACCEPT);
		assertThat(result.content()).containsEntry("asked", "Delete the ticket?").containsEntry("mode", "form");
		assertThat(registry.getCapabilities("modern-typed").elicitation()).isNotNull();
	}

	@Test
	void methodWithModernTypesAnswersModernFormAndUrlRequests() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());

		assertThat(registry.handleModernElicitation("modern-typed", new ElicitFormRequest("Delete?", SCHEMA, null))
			.content()).containsEntry("mode", "form");
		assertThat(registry
			.handleModernElicitation("modern-typed", new ElicitUrlRequest("Log in", "https://example.com/login", null))
			.content()).containsEntry("mode", "url");
	}

	@Test
	void structuredResultIsConvertedForAModernClient() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());

		ElicitResult result = registry.handleModernElicitation("structured",
				new ElicitFormRequest("Delete the ticket?", SCHEMA, null));

		assertThat(result).isEqualTo(ElicitResult.accept(Map.of("confirmed", true)));
	}

	@Test
	void modernRequestWithoutAHandlerForItsModeFails() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());
		var urlRequest = new ElicitUrlRequest("Log in", "https://example.com/login", null);
		var formRequest = new ElicitFormRequest("Delete?", SCHEMA, null);

		assertThatThrownBy(() -> registry.handleModernElicitation("legacy-typed", urlRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("URL")
			.hasMessageContaining("legacy-typed");
		assertThatThrownBy(() -> registry.handleModernElicitation("modern-url", formRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("form");
		assertThatThrownBy(() -> registry.handleModernElicitation("unknown", formRequest))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void modernProgressReachesTheMethodsOfBothSchemas() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory);
		Handlers handlers = beanFactory.getBean(Handlers.class);

		registry.handleModernProgress("progress", new ProgressParams("token-1", 10.0, 100.0, "asking", null));

		assertThat(handlers.progress).containsExactlyInAnyOrder(
				new McpSchema.ProgressNotification("token-1", 10.0, 100.0, "asking", null),
				new ProgressParams("token-1", 10.0, 100.0, "asking", null), "10.0 token-1 100.0");
	}

	@Test
	void legacyProgressReachesTheMethodsOfBothSchemas() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory);
		Handlers handlers = beanFactory.getBean(Handlers.class);

		registry.handleProgress("progress", new McpSchema.ProgressNotification("token-1", 10.0, 100.0, "asking", null));

		assertThat(handlers.progress).containsExactlyInAnyOrder(
				new McpSchema.ProgressNotification("token-1", 10.0, 100.0, "asking", null),
				new ProgressParams("token-1", 10.0, 100.0, "asking", null), "10.0 token-1 100.0");
	}

	@Test
	void numericProgressTokenOfAModernClientReachesTheMethodThatTakesTheValues() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory);
		Handlers handlers = beanFactory.getBean(Handlers.class);

		// A modern client numbers its progress tokens.
		registry.handleModernProgress("progress", new ProgressParams(7L, 10.0, 100.0, "asking", null));

		assertThat(handlers.progress).contains("10.0 7 100.0");
	}

	@Test
	void clientMayHaveOneMethodForFormRequestsAndOneForUrlRequests() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), FormAndUrlHandlers.class);

		assertThat(registry.handlesModernFormElicitation("both")).isTrue();
		assertThat(registry.handlesModernUrlElicitation("both")).isTrue();

		assertThat(registry.handleModernElicitation("both", new ElicitFormRequest("Delete?", SCHEMA, null)).content())
			.containsEntry("by", "form");
		assertThat(registry
			.handleModernElicitation("both", new ElicitUrlRequest("Log in", "https://example.com/login", null))
			.content()).containsEntry("by", "url");

		// A legacy client is only asked form requests, which the form method answers.
		assertThat(registry.getCapabilities("both").elicitation()).isNotNull();
		assertThat(
				registry.handleElicitation("both", new McpSchema.ElicitFormRequest("Delete?", SCHEMA, null)).content())
			.containsEntry("by", "form");
	}

	@Test
	void twoMethodsForTheSameKindOfRequestFailTheStartup() {
		for (Class<?> handlersClass : List.of(TwoFormHandlers.class, AnyAndUrlHandlers.class)) {
			assertThatThrownBy(() -> postProcessed(new DefaultListableBeanFactory(), handlersClass))
				.as(handlersClass.getSimpleName())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Found 2 elicitation handlers for client [both], found in bean with names [handlers]. "
						+ "Only one @McpElicitation handler is allowed per client, or one for form requests and one "
						+ "for URL requests");
		}
	}

	@Test
	void methodForUrlRequestsDeclaresNoElicitationForALegacyClient() {
		ClientMcpSyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory());

		assertThat(registry.getCapabilities("modern-url").elicitation()).isNull();
		assertThat(registry.getCapabilities("legacy-url").elicitation()).isNull();
		assertThat(registry.getCapabilities("legacy-typed").elicitation()).isNotNull();
	}

	@Test
	void annotationThatAMethodOnlyInheritsGetsNoHandlerAndDeclaresNothingForAModernClient() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory(), InheritingHandlers.class);
		var request = new ElicitFormRequest("Delete?", SCHEMA, null);

		// The implementing method does not repeat the annotation, so no handler is
		// created
		// for it, and a modern client must not declare a mode that nothing answers.
		assertThat(registry.handlesModernFormElicitation("inherited")).isFalse();
		assertThat(registry.hasHandlers("inherited", McpElicitation.class)).isFalse();
		assertThatThrownBy(() -> registry.handleModernElicitation("inherited", request))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void onlyASynchronousProgressMethodMakesAModernClientAskForProgress() {
		ClientMcpSyncHandlersRegistry registry = postProcessed(new DefaultListableBeanFactory(),
				ReactiveProgressHandlers.class);

		assertThat(registry.hasHandlers("reactive", McpProgress.class)).isTrue();
		assertThat(registry.handlesModernProgress("reactive")).isFalse();

		assertThat(postProcessed(new DefaultListableBeanFactory()).handlesModernProgress("progress")).isTrue();
		assertThat(postProcessed(new DefaultListableBeanFactory()).handlesModernProgress("unknown")).isFalse();
	}

	@Test
	void methodThatTakesFormRequestsFailsClearlyForAUrlRequest() {
		ClientMcpSyncHandlersRegistry registry = resolved(new DefaultListableBeanFactory());
		var urlRequest = new McpSchema.ElicitUrlRequest("Log in", "https://example.com/login", "e-1", null);

		// A legacy client declares form elicitation only, so a server should not send
		// this.
		assertThatThrownBy(() -> registry.handleElicitation("structured", urlRequest)).rootCause()
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("takes ElicitFormRequest")
			.hasMessageContaining("cannot answer a request of type ElicitUrlRequest");
	}

	@Test
	void elicitationHandlerReceivesTheToolContextOfTheToolCall() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory, ToolContextHandlers.class);
		ToolContextHandlers handlers = beanFactory.getBean(ToolContextHandlers.class);
		var request = new ElicitFormRequest("Delete the ticket?", SCHEMA, null);

		assertThat(registry.handlesModernFormElicitation("with-context")).isTrue();

		registry.handleModernElicitation("with-context", request, new ToolContext(Map.of("user", "alice")));
		registry.handleModernElicitation("with-context", request, new ToolContext(Map.of("user", "bob")));
		// Without a tool call at hand, as when the client is used directly.
		registry.handleModernElicitation("with-context", request);
		// A legacy request is not tied to a tool call.
		registry.handleElicitation("with-context", new McpSchema.ElicitFormRequest("Delete the ticket?", SCHEMA, null));

		assertThat(handlers.seen).containsExactly("asked alice", "asked bob", "asked nobody", "asked nobody");
	}

	@Test
	void toolContextMayComeBeforeTheRequest() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory, ToolContextHandlers.class);
		ToolContextHandlers handlers = beanFactory.getBean(ToolContextHandlers.class);

		registry.handleModernElicitation("context-first", new ElicitFormRequest("Delete?", SCHEMA, null),
				new ToolContext(Map.of("user", "alice")));

		assertThat(handlers.seen).containsExactly("asked alice");
	}

	@Test
	void progressHandlersReceiveTheToolContextOfTheToolCall() {
		var beanFactory = new DefaultListableBeanFactory();
		ClientMcpSyncHandlersRegistry registry = resolved(beanFactory, ToolContextHandlers.class);
		ToolContextHandlers handlers = beanFactory.getBean(ToolContextHandlers.class);
		var progress = new ProgressParams("token-1", 10.0, 100.0, "asking", null);

		registry.handleModernProgress("with-context", progress, new ToolContext(Map.of("user", "alice")));

		assertThat(handlers.seen).containsExactlyInAnyOrder("asking for alice", "10.0 token-1 100.0 for alice");

		handlers.seen.clear();
		registry.handleModernProgress("with-context", progress);
		registry.handleProgress("with-context",
				new McpSchema.ProgressNotification("token-1", 10.0, 100.0, "asking", null));

		assertThat(handlers.seen).containsExactlyInAnyOrder("asking for nobody", "10.0 token-1 100.0 for nobody",
				"asking for nobody", "10.0 token-1 100.0 for nobody");
	}

	private static ClientMcpSyncHandlersRegistry postProcessed(DefaultListableBeanFactory beanFactory) {
		return postProcessed(beanFactory, Handlers.class);
	}

	private static ClientMcpSyncHandlersRegistry postProcessed(DefaultListableBeanFactory beanFactory,
			Class<?> handlersClass) {
		var registry = new ClientMcpSyncHandlersRegistry();
		beanFactory.registerBeanDefinition("handlers",
				BeanDefinitionBuilder.genericBeanDefinition(handlersClass).getBeanDefinition());
		registry.postProcessBeanFactory(beanFactory);
		return registry;
	}

	private static ClientMcpSyncHandlersRegistry resolved(DefaultListableBeanFactory beanFactory) {
		return resolved(beanFactory, Handlers.class);
	}

	private static ClientMcpSyncHandlersRegistry resolved(DefaultListableBeanFactory beanFactory,
			Class<?> handlersClass) {
		ClientMcpSyncHandlersRegistry registry = postProcessed(beanFactory, handlersClass);
		registry.afterSingletonsInstantiated();
		return registry;
	}

	private static Object user(ToolContext toolContext) {
		return toolContext != null ? toolContext.getContext().get("user") : "nobody";
	}

	record Confirmation(boolean confirmed) {
	}

	interface AnnotatedHandlersContract {

		@McpElicitation(clients = "inherited")
		ElicitResult ask(ElicitFormRequest request);

	}

	/** Implements the annotated method without repeating the annotation. */
	static class InheritingHandlers implements AnnotatedHandlersContract {

		@Override
		public ElicitResult ask(ElicitFormRequest request) {
			return ElicitResult.accept(Map.of());
		}

	}

	static class ReactiveProgressHandlers {

		@McpProgress(clients = "reactive")
		Mono<Void> progress(McpSchema.ProgressNotification notification) {
			return Mono.empty();
		}

	}

	/** One method for the form requests and one for the URL requests of a client. */
	static class FormAndUrlHandlers {

		@McpElicitation(clients = "both")
		McpSchema.ElicitResult form(McpSchema.ElicitFormRequest request) {
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT)
				.content(Map.of("by", "form"))
				.build();
		}

		@McpElicitation(clients = "both")
		ElicitResult url(ElicitUrlRequest request) {
			return ElicitResult.accept(Map.of("by", "url"));
		}

	}

	static class TwoFormHandlers {

		@McpElicitation(clients = "both")
		ElicitResult form(ElicitFormRequest request) {
			return ElicitResult.decline();
		}

		@McpElicitation(clients = "both")
		McpSchema.ElicitResult formAgain(McpSchema.ElicitFormRequest request) {
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.DECLINE).build();
		}

	}

	/** A method that takes any request stands for URL requests as well. */
	static class AnyAndUrlHandlers {

		@McpElicitation(clients = "both")
		McpSchema.ElicitResult any(McpSchema.ElicitRequest request) {
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.DECLINE).build();
		}

		@McpElicitation(clients = "both")
		ElicitResult url(ElicitUrlRequest request) {
			return ElicitResult.decline();
		}

	}

	/** Handler methods that take the tool context of the tool call they serve. */
	static class ToolContextHandlers {

		final List<String> seen = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = "with-context")
		ElicitResult ask(ElicitFormRequest request, ToolContext toolContext) {
			this.seen.add("asked " + user(toolContext));
			return ElicitResult.accept(Map.of());
		}

		@McpElicitation(clients = "context-first")
		McpSchema.ElicitResult askLegacy(ToolContext toolContext, McpSchema.ElicitRequest request) {
			this.seen.add("asked " + user(toolContext));
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT).build();
		}

		@McpProgress(clients = "with-context")
		void progress(ProgressParams progress, ToolContext toolContext) {
			this.seen.add(progress.message() + " for " + user(toolContext));
		}

		@McpProgress(clients = "with-context")
		void progressValues(Double progress, String progressToken, String total, ToolContext toolContext) {
			this.seen.add(progress + " " + progressToken + " " + total + " for " + user(toolContext));
		}

	}

	static class Handlers {

		final List<Object> progress = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = "legacy-typed")
		McpSchema.ElicitResult legacyTyped(McpSchema.ElicitRequest request) {
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT)
				.content(Map.of("asked", request.message(), "by", "legacyTyped"))
				.meta(Map.of("left", "out"))
				.build();
		}

		@McpElicitation(clients = "modern-typed")
		ElicitResult modernTyped(ElicitRequest request) {
			return ElicitResult.accept(Map.of("asked", request.message(), "mode", request.mode()));
		}

		@McpElicitation(clients = "modern-url")
		ElicitResult modernUrl(ElicitUrlRequest request) {
			return ElicitResult.accept(null);
		}

		@McpElicitation(clients = "legacy-url")
		McpSchema.ElicitResult legacyUrl(McpSchema.ElicitUrlRequest request) {
			return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT).build();
		}

		@McpElicitation(clients = "structured")
		StructuredElicitResult<Confirmation> structured(ElicitFormRequest request) {
			return new StructuredElicitResult<>(McpSchema.ElicitResult.Action.ACCEPT, new Confirmation(true), null);
		}

		@McpProgress(clients = "progress")
		void legacyProgress(McpSchema.ProgressNotification notification) {
			this.progress.add(notification);
		}

		@McpProgress(clients = "progress")
		void modernProgress(ProgressParams progress) {
			this.progress.add(progress);
		}

		@McpProgress(clients = "progress")
		void progressValues(Double progress, String progressToken, String total) {
			this.progress.add(progress + " " + progressToken + " " + total);
		}

		@McpSampling(clients = "other")
		McpSchema.CreateMessageResult sampling(McpSchema.CreateMessageRequest request) {
			throw new UnsupportedOperationException();
		}

		@McpLogging(clients = "other")
		void logging(McpSchema.LoggingMessageNotification notification) {
		}

	}

}
