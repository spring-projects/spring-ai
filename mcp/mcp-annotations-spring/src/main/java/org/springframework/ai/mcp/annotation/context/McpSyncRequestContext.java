/*
 * Copyright 2025-2025 the original author or authors.
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

import java.util.function.Consumer;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ListRootsResult;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import tools.jackson.core.type.TypeReference;

/**
 * @author Christian Tzolov
 */
public interface McpSyncRequestContext extends McpRequestContextTypes<McpSyncServerExchange> {

	// --------------------------------------
	// Roots
	// --------------------------------------
	boolean rootsEnabled();

	ListRootsResult roots();

	// --------------------------------------
	// Elicitation
	// --------------------------------------
	boolean elicitEnabled();

	<T> StructuredElicitResult<T> elicit(Class<T> type);

	<T> StructuredElicitResult<T> elicit(TypeReference<T> type);

	<T> StructuredElicitResult<T> elicit(Consumer<ElicitationSpec> params, Class<T> returnType);

	<T> StructuredElicitResult<T> elicit(Consumer<ElicitationSpec> params, TypeReference<T> returnType);

	ElicitResult elicit(ElicitRequest elicitRequest);

	// --------------------------------------
	// Sampling
	// --------------------------------------
	boolean sampleEnabled();

	CreateMessageResult sample(String... messages);

	CreateMessageResult sample(Consumer<SamplingSpec> samplingSpec);

	CreateMessageResult sample(CreateMessageRequest createMessageRequest);

	// --------------------------------------
	// Progress
	// --------------------------------------
	void progress(int percentage);

	void progress(Consumer<ProgressSpec> progressSpec);

	void progress(ProgressNotification progressNotification);

	// --------------------------------------
	// Ping
	// --------------------------------------
	void ping();

	// --------------------------------------
	// Logging
	// --------------------------------------
	void log(Consumer<LoggingSpec> logSpec);

	void debug(String message);

	void info(String message);

	void warn(String message);

	void error(String message);

}
