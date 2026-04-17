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

package org.springframework.ai.mcp.annotation.context;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListRootsResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;

import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonParser;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Async (Reactor) implementation of McpAsyncRequestContext that returns Mono of value
 * types.
 *
 * @author Christian Tzolov
 */
public final class DefaultMcpAsyncRequestContext implements McpAsyncRequestContext {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMcpAsyncRequestContext.class);

	private static final Map<Type, Map<String, Object>> typeSchemaCache = new ConcurrentReferenceHashMap<>(256);

	private static TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {
	};

	private final McpSchema.Request request;

	private final McpAsyncServerExchange exchange;

	private DefaultMcpAsyncRequestContext(McpSchema.Request request, McpAsyncServerExchange exchange) {
		Assert.notNull(request, "Request must not be null");
		Assert.notNull(exchange, "Exchange must not be null");
		this.request = request;
		this.exchange = exchange;
	}

	// Roots

	@Override
	public Mono<Boolean> rootsEnabled() {
		return Mono.just(!(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().roots() == null));
	}

	@Override
	public Mono<ListRootsResult> roots() {
		return this.rootsEnabled().flatMap(enabled -> {
			if (!enabled) {
				return Mono.error(new IllegalStateException(
						"Roots not supported by the client: " + this.exchange.getClientInfo()));
			}
			return this.exchange.listRoots();
		});
	}

	// Elicitation

	@Override
	public Mono<Boolean> elicitEnabled() {
		return Mono.just(!(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().elicitation() == null));
	}

	@Override
	public <T> Mono<StructuredElicitResult<T>> elicit(Consumer<ElicitationSpec> spec, TypeReference<T> type) {
		Assert.notNull(type, "Elicitation response type must not be null");
		Assert.notNull(spec, "Elicitation spec consumer must not be null");
		DefaultElicitationSpec elicitationSpec = new DefaultElicitationSpec();
		spec.accept(elicitationSpec);
		return this.elicitationInternal(elicitationSpec.message, type.getType(), elicitationSpec.meta)
			.map(er -> new StructuredElicitResult<T>(er.action(), McpJsonParser.fromMap(er.content(), type),
					er.meta()));
	}

	@Override
	public <T> Mono<StructuredElicitResult<T>> elicit(Consumer<ElicitationSpec> spec, Class<T> type) {
		Assert.notNull(type, "Elicitation response type must not be null");
		Assert.notNull(spec, "Elicitation spec consumer must not be null");
		DefaultElicitationSpec elicitationSpec = new DefaultElicitationSpec();
		spec.accept(elicitationSpec);
		return this.elicitationInternal(elicitationSpec.message, type, elicitationSpec.meta)
			.map(er -> new StructuredElicitResult<T>(er.action(), McpJsonParser.fromMap(er.content(), type),
					er.meta()));
	}

	@Override
	public <T> Mono<StructuredElicitResult<T>> elicit(TypeReference<T> type) {
		Assert.notNull(type, "Elicitation response type must not be null");
		return this.elicitationInternal("Please provide the required information.", type.getType(), null)
			.map(er -> new StructuredElicitResult<T>(er.action(), McpJsonParser.fromMap(er.content(), type),
					er.meta()));
	}

	@Override
	public <T> Mono<StructuredElicitResult<T>> elicit(Class<T> type) {
		Assert.notNull(type, "Elicitation response type must not be null");
		return this.elicitationInternal("Please provide the required information.", type, null)
			.map(er -> new StructuredElicitResult<T>(er.action(), McpJsonParser.fromMap(er.content(), type),
					er.meta()));
	}

	@Override
	public Mono<ElicitResult> elicit(ElicitRequest elicitRequest) {
		Assert.notNull(elicitRequest, "Elicit request must not be null");

		return this.elicitEnabled().flatMap(enabled -> {
			if (!enabled) {
				return Mono.error(new IllegalStateException(
						"Elicitation not supported by the client: " + this.exchange.getClientInfo()));
			}
			return this.exchange.createElicitation(elicitRequest);
		});
	}

	public Mono<ElicitResult> elicitationInternal(String message, Type type, Map<String, Object> meta) {
		Assert.hasText(message, "Elicitation message must not be empty");
		Assert.notNull(type, "Elicitation response type must not be null");

		// TODO add validation for the Elicitation Schema
		// https://modelcontextprotocol.io/specification/2025-06-18/client/elicitation#supported-schema-types

		Map<String, Object> schema = typeSchemaCache.computeIfAbsent(type, t -> this.generateElicitSchema(t));

		return this.elicit(ElicitRequest.builder().message(message).requestedSchema(schema).meta(meta).build());
	}

	private Map<String, Object> generateElicitSchema(Type type) {
		Map<String, Object> schema = JsonParser.fromJson(McpJsonSchemaGenerator.generateFromType(type), MAP_TYPE_REF);
		// remove as elicitation schema does not support it
		schema.remove("$schema");
		return schema;
	}

	// Sampling

	@Override
	public Mono<Boolean> sampleEnabled() {
		return Mono.just(!(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().sampling() == null));
	}

	@Override
	public Mono<CreateMessageResult> sample(String... messages) {
		return this.sample(s -> s.message(messages));
	}

	@Override
	public Mono<CreateMessageResult> sample(Consumer<SamplingSpec> samplingSpec) {
		Assert.notNull(samplingSpec, "Sampling spec consumer must not be null");
		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		samplingSpec.accept(spec);

		var progressToken = this.request.progressToken();

		if (progressToken == null || (progressToken instanceof String pt && !Utils.hasText(pt))) {
			logger.warn("Progress notification not supported by the client!");
		}
		return this.sample(McpSchema.CreateMessageRequest.builder()
			.messages(spec.messages)
			.modelPreferences(spec.modelPreferences)
			.systemPrompt(spec.systemPrompt)
			.temperature(spec.temperature)
			.maxTokens(spec.maxTokens != null && spec.maxTokens > 0 ? spec.maxTokens : 500)
			.stopSequences(spec.stopSequences.isEmpty() ? null : spec.stopSequences)
			.includeContext(spec.includeContextStrategy)
			.meta(spec.metadata.isEmpty() ? null : spec.metadata)
			.progressToken(progressToken)
			.meta(spec.meta.isEmpty() ? null : spec.meta)
			.build());
	}

	@Override
	public Mono<CreateMessageResult> sample(CreateMessageRequest createMessageRequest) {

		return this.sampleEnabled().flatMap(enabled -> {
			if (!enabled) {
				return Mono.error(new IllegalStateException(
						"Sampling not supported by the client: " + this.exchange.getClientInfo()));
			}
			return this.exchange.createMessage(createMessageRequest);
		});
	}

	// Progress

	@Override
	public Mono<Void> progress(int percentage) {
		Assert.isTrue(percentage >= 0 && percentage <= 100, "Percentage must be between 0 and 100");
		return this.progress(p -> p.progress(percentage / 100.0).total(1.0).message(null));
	}

	@Override
	public Mono<Void> progress(Consumer<ProgressSpec> progressSpec) {

		Assert.notNull(progressSpec, "Progress spec consumer must not be null");
		DefaultProgressSpec spec = new DefaultProgressSpec();

		progressSpec.accept(spec);

		var progressToken = this.request.progressToken();

		if (progressToken == null || (progressToken instanceof String pt && !Utils.hasText(pt))) {
			logger.warn("Progress notification not supported by the client!");
			return Mono.empty();
		}

		return this
			.progress(new ProgressNotification(progressToken, spec.progress, spec.total, spec.message, spec.meta));
	}

	@Override
	public Mono<Void> progress(ProgressNotification progressNotification) {
		return this.exchange.progressNotification(progressNotification).then(Mono.<Void>empty());
	}

	// Ping

	@Override
	public Mono<Object> ping() {
		return this.exchange.ping();
	}

	// Logging

	@Override
	public Mono<Void> log(Consumer<LoggingSpec> logSpec) {
		Assert.notNull(logSpec, "Logging spec consumer must not be null");
		DefaultLoggingSpec spec = new DefaultLoggingSpec();
		logSpec.accept(spec);

		return this.exchange
			.loggingNotification(LoggingMessageNotification.builder()
				.data(spec.message)
				.level(spec.level)
				.logger(spec.logger)
				.meta(spec.meta)
				.build())
			.then();
	}

	@Override
	public Mono<Void> debug(String message) {
		return this.logInternal(message, LoggingLevel.DEBUG);
	}

	@Override
	public Mono<Void> info(String message) {
		return this.logInternal(message, LoggingLevel.INFO);
	}

	@Override
	public Mono<Void> warn(String message) {
		return this.logInternal(message, LoggingLevel.WARNING);
	}

	@Override
	public Mono<Void> error(String message) {
		return this.logInternal(message, LoggingLevel.ERROR);
	}

	private Mono<Void> logInternal(String message, LoggingLevel level) {
		Assert.hasText(message, "Log message must not be empty");
		return this.exchange
			.loggingNotification(LoggingMessageNotification.builder().data(message).level(level).build())
			.then();
	}

	// Getters

	@Override
	public McpSchema.Request request() {
		return this.request;
	}

	@Override
	public McpAsyncServerExchange exchange() {
		return this.exchange;
	}

	@Override
	public String sessionId() {
		return this.exchange.sessionId();
	}

	@Override
	public Implementation clientInfo() {
		return this.exchange.getClientInfo();
	}

	@Override
	public ClientCapabilities clientCapabilities() {
		return this.exchange.getClientCapabilities();
	}

	@Override
	public Map<String, Object> requestMeta() {
		return this.request.meta();
	}

	@Override
	public McpTransportContext transportContext() {
		return this.exchange.transportContext();
	}

	// Builder

	public static Builder builder() {
		return new Builder();
	}

	public final static class Builder {

		private McpSchema.Request request;

		private McpAsyncServerExchange exchange;

		private Builder() {
		}

		public Builder request(McpSchema.Request request) {
			this.request = request;
			return this;
		}

		public Builder exchange(McpAsyncServerExchange exchange) {
			this.exchange = exchange;
			return this;
		}

		public McpAsyncRequestContext build() {
			return new DefaultMcpAsyncRequestContext(this.request, this.exchange);
		}

	}

}
