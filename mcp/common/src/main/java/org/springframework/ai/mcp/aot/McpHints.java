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

package org.springframework.ai.mcp.aot;

import java.util.Set;

import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Runtime hints registrar for Model Context Protocol (MCP) schema classes.
 * <p>
 * This class provides GraalVM native image hints for MCP schema classes to ensure proper
 * reflection access in native images. It:
 * <ul>
 * <li>Registers all nested classes of {@link McpSchema} for reflection</li>
 * <li>Enables all member categories (fields, methods, etc.) for registered types</li>
 * <li>Ensures proper serialization/deserialization in native images</li>
 * </ul>
 *
 * @author Josh Long
 * @since 1.0.0
 * @see RuntimeHintsRegistrar
 * @see McpSchema
 */
@SuppressWarnings("unused")
public class McpHints implements RuntimeHintsRegistrar {

	/**
	 * Registers runtime hints for MCP schema classes.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Discovers all nested classes within {@link McpSchema}</li>
	 * <li>Registers each discovered class for reflection access</li>
	 * <li>Enables all member categories for complete reflection support</li>
	 * </ol>
	 * @param hints the hints instance to register hints with
	 * @param classLoader the classloader to use (may be null)
	 */
	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();

		Set<TypeReference> typeReferences = AiRuntimeHints.findInnerClassesFor(McpSchema.class);
		for (var tr : typeReferences) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}
