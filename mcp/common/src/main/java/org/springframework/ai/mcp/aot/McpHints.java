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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
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
 * @author Wenli Tian
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
	 * @param hints       the hints instance to register hints with
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
	 * This method iteratively finds all nested classes (both declared and inherited) of
	 * the provided class and converts them to type references.
	 * @param clazz the class to find inner classes for
	 * @return a set of type references for all discovered inner classes
	 */
	private Set<TypeReference> innerClasses(Class<?> clazz) {
		Set<String> classNames = new HashSet<>();
		findNestedClassesIteratively(clazz, classNames);
		return classNames.stream().map(TypeReference::of).collect(Collectors.toSet());
	}

	/**
	 * Iteratively finds all nested classes of a given class, avoiding potential
	 * stack overflow from recursion.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Uses a queue to store classes to be processed</li>
	 * <li>Iteratively processes each class in the queue, finding its declared and
	 * inherited nested classes</li>
	 * <li>Adds the found class names to the provided set</li>
	 * </ol>
	 * 
	 * @param rootClass  the root class to find nested classes for
	 * @param classNames the set to collect class names in
	 */
	private void findNestedClassesIteratively(Class<?> rootClass, Set<String> classNames) {
		Queue<Class<?>> queue = new ArrayDeque<>();
		queue.add(rootClass);

		// Use breadth-first search to process all nested classes
		while (!queue.isEmpty()) {
			Class<?> current = queue.poll();

			// Skip the root class itself
			if (current != rootClass) {
				classNames.add(current.getName());
			}

			// Collect declared and inherited nested classes
			ArrayList<Class<?>> nestedClasses = new ArrayList<>();
			nestedClasses.addAll(Arrays.asList(current.getDeclaredClasses()));
			nestedClasses.addAll(Arrays.asList(current.getClasses()));

			// Add found nested classes to the queue for further processing
			queue.addAll(nestedClasses);
		}
	}

}
