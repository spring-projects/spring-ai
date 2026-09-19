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

package org.springframework.ai.mcp.toolbox.aot;

import java.util.List;

import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.transport.TransportManifest;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM Native Image {@link RuntimeHintsRegistrar} for Google Cloud MCP Toolbox schema
 * and JSON-RPC transport records.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
public class McpToolboxRuntimeHints implements RuntimeHintsRegistrar {

	private static final List<Class<?>> PUBLIC_TYPES = List.of(ToolDefinition.class, ToolDefinition.Parameter.class,
			ToolResult.class, ToolResult.Content.class, TransportManifest.class);

	private static final List<String> PACKAGE_PRIVATE_TYPE_NAMES = List.of("com.google.cloud.mcp.transport.JsonRpc",
			"com.google.cloud.mcp.transport.JsonRpc$Request", "com.google.cloud.mcp.transport.JsonRpc$Response",
			"com.google.cloud.mcp.transport.JsonRpc$Error", "com.google.cloud.mcp.transport.JsonRpc$Notification");

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		MemberCategory[] categories = MemberCategory.values();
		for (Class<?> type : PUBLIC_TYPES) {
			hints.reflection().registerType(TypeReference.of(type), categories);
			for (Class<?> declaredClass : type.getDeclaredClasses()) {
				hints.reflection().registerType(TypeReference.of(declaredClass), categories);
			}
		}
		for (String typeName : PACKAGE_PRIVATE_TYPE_NAMES) {
			hints.reflection().registerType(TypeReference.of(typeName), categories);
		}
	}

}
