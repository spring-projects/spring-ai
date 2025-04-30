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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.Nullable;

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

		for (var tr : innerClasses(McpSchema.class)) {
			hints.reflection().registerType(tr, mcs);
		}
	}

	/**
	 * Discovers all inner classes of a given class.
	 * <p>
	 * This method recursively finds all nested classes (both declared and inherited) of
	 * the provided class and converts them to type references.
	 * @param clazz the class to find inner classes for
	 * @return a set of type references for all discovered inner classes
	 */
	private Set<TypeReference> innerClasses(Class<?> clazz) {
		var indent = new HashSet<String>();
		this.findNestedClasses(clazz, indent);
		return indent.stream().map(TypeReference::of).collect(Collectors.toSet());
	}

	/**
	 * Recursively finds all nested classes of a given class.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Collects both declared and inherited nested classes</li>
	 * <li>Recursively processes each nested class</li>
	 * <li>Adds the class names to the provided set</li>
	 * </ol>
	 * @param clazz the class to find nested classes for
	 * @param indent the set to collect class names in
	 */
	private void findNestedClasses(Class<?> clazz, Set<String> indent) {
		var classes = new ArrayList<Class<?>>();
		classes.addAll(Arrays.asList(clazz.getDeclaredClasses()));
		classes.addAll(Arrays.asList(clazz.getClasses()));
		for (var nestedClass : classes) {
			this.findNestedClasses(nestedClass, indent);
		}
		indent.addAll(classes.stream().map(Class::getName).toList());
	}

}
