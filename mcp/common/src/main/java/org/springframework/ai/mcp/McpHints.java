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
package org.springframework.ai.mcp;

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
 * @author Josh Long
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class McpHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();

		for (var tr : innerClasses(McpSchema.class)) {
			hints.reflection().registerType(tr, mcs);
		}
	}

	private Set<TypeReference> innerClasses(Class<?> clazz) {
		var indent = new HashSet<String>();
		this.findNestedClasses(clazz, indent);
		return indent.stream().map(TypeReference::of).collect(Collectors.toSet());
	}

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
