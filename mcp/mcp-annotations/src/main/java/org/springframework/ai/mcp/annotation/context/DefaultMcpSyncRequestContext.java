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
import io.modelcontextprotocol.server.McpSyncServerExchange;
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
import tools.jackson.core.type.TypeReference;

import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonParser;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * @author Christian Tzolov
 */
public final class DefaultMcpSyncRequestContext implements McpSyncRequestContext {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMcpSyncRequestContext.class);

	private static final Map<Type, Map<String, Object>> typeSchemaCache = new ConcurrentReferenceHashMap<>(256);

	private static TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {
	};

	private final McpSchema.Request request;

	private final McpSyncServerExchange exchange;

	private DefaultMcpSyncRequestContext(McpSchema.Request request, McpSyncServerExchange exchange) {
		Assert.notNull(request, "Request must not be null");
		Assert.notNull(exchange, "Exchange must not be null");
		this.request = request;
		this.exchange = exchange;
	}

	// Roots

	@Override
	public boolean rootsEnabled() {
		return !(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().roots() == null);
	}

	@Override
	public ListRootsResult roots() {
		if (!this.rootsEnabled()) {
			throw new IllegalStateException("Roots not supported by the client: " + this.exchange.getClientInfo());
		}
		return this.exchange.listRoots();
	}

	// Elicitation

	@Override
	public boolean elicitEnabled() {
		return !(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().elicitation() == null);
	}

	@Override
	public <T> StructuredElicitResult<T> elicit(Class<T> type) {

		if (!this.elicitEnabled()) {
			throw new IllegalStateException(
					"Elicitation not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(type, "Elicitation response type must not be null");

		ElicitResult elicitResult = this.elicitationInternal("Please provide the required information.", type, null);

		if (elicitResult.action() != ElicitResult.Action.ACCEPT) {
			return new StructuredElicitResult<>(elicitResult.action(), null, elicitResult.meta());
		}

		return new StructuredElicitResult<>(elicitResult.action(), McpJsonParser.fromMap(elicitResult.content(), type),
				elicitResult.meta());
	}

	@Override
	public <T> StructuredElicitResult<T> elicit(TypeReference<T> type) {

		if (!this.elicitEnabled()) {
			throw new IllegalStateException(
					"Elicitation not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(type, "Elicitation response type must not be null");

		ElicitResult elicitResult = this.elicitationInternal("Please provide the required information.", type.getType(),
				null);

		if (elicitResult.action() != ElicitResult.Action.ACCEPT) {
			return new StructuredElicitResult<>(elicitResult.action(), null, elicitResult.meta());
		}

		return new StructuredElicitResult<>(elicitResult.action(),

				McpJsonParser.fromMap(elicitResult.content(), type), elicitResult.meta());
	}

	@Override
	public <T> StructuredElicitResult<T> elicit(Consumer<ElicitationSpec> params, Class<T> returnType) {

		if (!this.elicitEnabled()) {
			throw new IllegalStateException(
					"Elicitation not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(returnType, "Elicitation response type must not be null");
		Assert.notNull(params, "Elicitation params must not be null");

		DefaultElicitationSpec paramSpec = new DefaultElicitationSpec();

		params.accept(paramSpec);

		ElicitResult elicitResult = this.elicitationInternal(paramSpec.message(), returnType, paramSpec.meta());

		if (elicitResult.action() != ElicitResult.Action.ACCEPT) {
			return new StructuredElicitResult<>(elicitResult.action(), null, null);
		}

		return new StructuredElicitResult<>(elicitResult.action(),
				McpJsonParser.fromMap(elicitResult.content(), returnType), elicitResult.meta());
	}

	@Override
	public <T> StructuredElicitResult<T> elicit(Consumer<ElicitationSpec> params, TypeReference<T> returnType) {

		if (!this.elicitEnabled()) {
			throw new IllegalStateException(
					"Elicitation not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(returnType, "Elicitation response type must not be null");
		Assert.notNull(params, "Elicitation params must not be null");

		DefaultElicitationSpec paramSpec = new DefaultElicitationSpec();
		params.accept(paramSpec);

		ElicitResult elicitResult = this.elicitationInternal(paramSpec.message(), returnType.getType(),
				paramSpec.meta());

		if (elicitResult.action() != ElicitResult.Action.ACCEPT) {
			return new StructuredElicitResult<>(elicitResult.action(), null, null);
		}

		return new StructuredElicitResult<>(elicitResult.action(),
				McpJsonParser.fromMap(elicitResult.content(), returnType), elicitResult.meta());
	}

	@Override
	public ElicitResult elicit(ElicitRequest elicitRequest) {
		if (!this.elicitEnabled()) {
			throw new IllegalStateException(
					"Elicitation not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(elicitRequest, "Elicit request must not be null");

		return this.exchange.createElicitation(elicitRequest);
	}

	private ElicitResult elicitationInternal(String message, Type type, Map<String, Object> meta) {

		// TODO add validation for the Elicitation Schema
		// https://modelcontextprotocol.io/specification/2025-06-18/client/elicitation#supported-schema-types

		Map<String, Object> schema = typeSchemaCache.computeIfAbsent(type, t -> this.generateElicitSchema(t));

		ElicitRequest elicitRequest = ElicitRequest.builder()
			.message(message)
			.requestedSchema(schema)
			.meta(meta)
			.build();

		return this.exchange.createElicitation(elicitRequest);
	}

	private Map<String, Object> generateElicitSchema(Type type) {
		Map<String, Object> schema = JsonParser.fromJson(McpJsonSchemaGenerator.generateFromType(type), MAP_TYPE_REF);
		// remove $schema as elicitation schema does not support it
		schema.remove("$schema");
		return schema;
	}

	// Sampling

	@Override
	public boolean sampleEnabled() {
		return !(this.exchange.getClientCapabilities() == null
				|| this.exchange.getClientCapabilities().sampling() == null);
	}

	@Override
	public CreateMessageResult sample(String... messages) {
		return this.sample(s -> s.message(messages));
	}

	@Override
	public CreateMessageResult sample(Consumer<SamplingSpec> samplingSpec) {

		if (!this.sampleEnabled()) {
			throw new IllegalStateException("Sampling not supported by the client: " + this.exchange.getClientInfo());
		}

		Assert.notNull(samplingSpec, "Sampling spec consumer must not be null");

		DefaultSamplingSpec spec = new DefaultSamplingSpec();
		samplingSpec.accept(spec);

		var progressToken = this.request.progressToken();

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
	public CreateMessageResult sample(CreateMessageRequest createMessageRequest) {

		if (!this.sampleEnabled()) {
			throw new IllegalStateException("Sampling not supported by the client: " + this.exchange.getClientInfo());
		}

		return this.exchange.createMessage(createMessageRequest);
	}

	// Progress

	@Override
	public void progress(int percentage) {
		Assert.isTrue(percentage >= 0 && percentage <= 100, "Percentage must be between 0 and 100");
		this.progress(p -> p.progress(percentage / 100.0).total(1.0).message(null));
	}

	@Override
	public void progress(Consumer<ProgressSpec> progressSpec) {

		Assert.notNull(progressSpec, "Progress spec consumer must not be null");
		DefaultProgressSpec spec = new DefaultProgressSpec();

		progressSpec.accept(spec);

		var progressToken = this.request.progressToken();

		if (progressToken == null || (progressToken instanceof String pt && !Utils.hasText(pt))) {
			logger.warn("Progress notification not supported by the client!");
			return;
		}

		this.progress(new ProgressNotification(progressToken, spec.progress, spec.total, spec.message, spec.meta));
	}

	@Override
	public void progress(ProgressNotification progressNotification) {
		this.exchange.progressNotification(progressNotification);
	}

	// Ping

	@Override
	public void ping() {
		this.exchange.ping();
	}

	// Logging

	@Override
	public void log(Consumer<LoggingSpec> logSpec) {
		Assert.notNull(logSpec, "Logging spec consumer must not be null");
		DefaultLoggingSpec spec = new DefaultLoggingSpec();
		logSpec.accept(spec);

		this.exchange.loggingNotification(LoggingMessageNotification.builder()
			.data(spec.message)
			.level(spec.level)
			.logger(spec.logger)
			.meta(spec.meta)
			.build());
	}

	@Override
	public void debug(String message) {
		this.logInternal(message, LoggingLevel.DEBUG);
	}

	@Override
	public void info(String message) {
		this.logInternal(message, LoggingLevel.INFO);
	}

	@Override
	public void warn(String message) {
		this.logInternal(message, LoggingLevel.WARNING);
	}

	@Override
	public void error(String message) {
		this.logInternal(message, LoggingLevel.ERROR);
	}

	private void logInternal(String message, LoggingLevel level) {
		Assert.hasText(message, "Log message must not be empty");
		this.exchange.loggingNotification(LoggingMessageNotification.builder().data(message).level(level).build());
	}

	// Getters

	@Override
	public McpSchema.Request request() {
		return this.request;
	}

	@Override
	public McpSyncServerExchange exchange() {
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

		private McpSyncServerExchange exchange;

		private Builder() {
		}

		public Builder request(McpSchema.Request request) {
			this.request = request;
			return this;
		}

		public Builder exchange(McpSyncServerExchange exchange) {
			this.exchange = exchange;
			return this;
		}

		public McpSyncRequestContext build() {
			return new DefaultMcpSyncRequestContext(this.request, this.exchange);
		}

	}

}
