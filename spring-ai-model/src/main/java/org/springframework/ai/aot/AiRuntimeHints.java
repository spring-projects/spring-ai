/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.aot;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

/**
 * Utility methods for creating native runtime hints. See other modules for their
 * respective native runtime hints.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Fu Jian
 */
public abstract class AiRuntimeHints {

	private static final Logger log = LoggerFactory.getLogger(AiRuntimeHints.class);

	/**
	 * Finds classes in a package that are annotated with JsonInclude or have Jackson
	 * annotations.
	 * @param packageName The name of the package to search for annotated classes.
	 * @return A set of TypeReference objects representing the annotated classes found.
	 */
	public static Set<TypeReference> findJsonAnnotatedClassesInPackage(String packageName) {
		var annotationTypeFilter = new AnnotationTypeFilter(JsonInclude.class);
		TypeFilter typeFilter = (metadataReader, metadataReaderFactory) -> {
			try {
				var clazz = Class.forName(metadataReader.getClassMetadata().getClassName());
				return annotationTypeFilter.match(metadataReader, metadataReaderFactory)
						|| !discoverJacksonAnnotatedTypesFromRootType(clazz).isEmpty();
			}
			catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		};

		return findClassesInPackage(packageName, typeFilter);
	}

	/**
	 * Finds classes in a package that are annotated with JsonInclude or have Jackson
	 * annotations.
	 * @param packageClass The class in the package to search for annotated classes.
	 * @return A set of TypeReference objects representing the annotated classes found.
	 */
	public static Set<TypeReference> findJsonAnnotatedClassesInPackage(Class<?> packageClass) {
		return findJsonAnnotatedClassesInPackage(packageClass.getPackageName());
	}

	/**
	 * Finds all classes in the specified package that match the given type filter.
	 * @param packageName The name of the package to scan for classes.
	 * @param typeFilter The type filter used to filter the scanned classes.
	 * @return A set of TypeReference objects representing the found classes.
	 */
	public static Set<TypeReference> findClassesInPackage(String packageName, TypeFilter typeFilter) {
		var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
		classPathScanningCandidateComponentProvider.addIncludeFilter(typeFilter);
		return classPathScanningCandidateComponentProvider//
			.findCandidateComponents(packageName)//
			.stream()//
			.map(bd -> TypeReference.of(Objects.requireNonNull(bd.getBeanClassName())))//
			.peek(tr -> {
				if (log.isDebugEnabled()) {
					log.debug("registering [{}]", tr.getName());
				}
			})
			.collect(Collectors.toUnmodifiableSet());
	}

	private static boolean hasJacksonAnnotations(Class<?> type) {
		var annotationsToFind = Set.of(JsonProperty.class, JsonInclude.class);
		for (var annotationToFind : annotationsToFind) {

			if (type.isAnnotationPresent(annotationToFind)) {
				return true;
			}

			var executables = new HashSet<Executable>();
			executables.addAll(Set.of(type.getMethods()));
			executables.addAll(Set.of(type.getConstructors()));
			executables.addAll(Set.of(type.getDeclaredConstructors()));

			for (var executable : executables) {
				if (executable.isAnnotationPresent(annotationToFind)) {
					return true;
				}

				for (var p : executable.getParameters()) {
					if (p.isAnnotationPresent(annotationToFind)) {
						return true;
					}
				}
			}

			if (type.getRecordComponents() != null) {
				for (var r : type.getRecordComponents()) {
					if (r.isAnnotationPresent(annotationToFind)) {
						return true;
					}
				}
			}

			for (var f : type.getFields()) {
				if (f.isAnnotationPresent(annotationToFind)) {
					return true;
				}
			}
		}

		return false;
	}

	private static Set<Class<?>> discoverJacksonAnnotatedTypesFromRootType(Class<?> type) {
		var jsonTypes = new HashSet<Class<?>>();
		var classesToInspect = new HashSet<Class<?>>();
		classesToInspect.add(type);
		classesToInspect.addAll(Arrays.asList(type.getNestMembers()));
		for (var n : classesToInspect) {
			if (hasJacksonAnnotations(n)) {
				jsonTypes.add(n);
			}
		}
		return jsonTypes;
	}

	/**
	 * Discovers all inner classes of a given class.
	 * <p>
	 * This method recursively finds all nested classes (both declared and inherited) of
	 * the provided class and converts them to type references.
	 * @param clazz the class to find inner classes for
	 * @return a set of type references for all discovered inner classes
	 */
	public static Set<TypeReference> findInnerClassesFor(Class<?> clazz) {
		var indent = new HashSet<String>();
		findNestedClasses(clazz, indent);
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
	private static void findNestedClasses(Class<?> clazz, Set<String> indent) {
		var classes = new ArrayList<Class<?>>();
		classes.addAll(Arrays.asList(clazz.getDeclaredClasses()));
		classes.addAll(Arrays.asList(clazz.getClasses()));
		for (var nestedClass : classes) {
			findNestedClasses(nestedClass, indent);
		}
		indent.addAll(classes.stream().map(Class::getName).toList());
	}

}
