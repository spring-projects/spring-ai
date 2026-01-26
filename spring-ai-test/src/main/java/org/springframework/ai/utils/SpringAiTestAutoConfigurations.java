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

package org.springframework.ai.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Utility class that creates {@link AutoConfigurations} for testing purpose.
 * <p>
 * This class processes the provided configuration classes, checks for the presence of
 * {@link AutoConfiguration} annotations, and builds an {@link AutoConfigurations}
 * instance that includes both the original classes and those declared in the
 * {@link AutoConfiguration#after()} attribute.
 * </p>
 *
 * @author Issam El-atif
 * @author Eric BOTTARD
 * @since 1.1.0
 * @see AutoConfigurations
 */
public final class SpringAiTestAutoConfigurations {

	private static final String NAME = "META-INF/spring/" + AutoConfiguration.class.getName() + ".imports";

	private SpringAiTestAutoConfigurations() {
	}

	public static AutoConfigurations forThisModuleAnd(Class<?>... configurations) {
		// Craft a ClassLoader dedicated to loading ONLY the imports file for
		// the "current" module (as defined by current working directory for each maven
		// module)
		ClassLoader cl = new ClassLoader() {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				throw new UnsupportedOperationException();
			}

			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				Assert.state(NAME.equals(name), "Expected to be called with " + NAME);
				File file = new File("target/classes/" + NAME);
				Assert.state(file.exists(), "Expected %s to exist".formatted(file.getAbsolutePath()));
				return Collections.enumeration(Set.of(file.toURI().toURL()));
			}
		};
		// Use ImportCandidates to parse the imports file (takes care of comments, etc.)
		List<String> classNames = ImportCandidates.load(AutoConfiguration.class, cl).getCandidates();
		// Merge the result with the explicit additionalAutoConfigurations
		Class<?>[] classes = new Class[classNames.size() + configurations.length];
		for (int i = 0; i < classNames.size(); i++) {
			classes[i] = ClassUtils.resolveClassName(classNames.get(i), null);
		}
		System.arraycopy(configurations, 0, classes, classNames.size(), configurations.length);
		return AutoConfigurations.of(classes);

	}

	/**
	 * Creates an {@link AutoConfigurations} instance that includes the provided
	 * configuration classes and any autoconfiguration classes referenced in
	 * {@link AutoConfiguration#after()} attribute.
	 * @param configurations one or more configuration classes that may be annotated with
	 * {@link AutoConfiguration}
	 * @return a composed {@link AutoConfigurations} instance including all discovered
	 * classes
	 * @see AutoConfigurations#of(Class[])
	 * @deprecated {@link AutoConfiguration#after()} is NOT a way to declare dependencies
	 * between AutoConfigurations and thus does not guarantee that other configurations
	 * will be visible at runtime. Use {@link #forThisModuleAnd(Class[])} instead, listing
	 * other additional configs manually if they contain beans required for a particular
	 * test.
	 * @see design/01-autoconfigurations.adoc
	 */
	@Deprecated
	public static AutoConfigurations of(Class<?>... configurations) {
		return AutoConfigurations.of(Arrays.stream(configurations)
			.map(c -> AnnotationUtils.findAnnotation(c, AutoConfiguration.class))
			.filter(Objects::nonNull)
			.map(AutoConfiguration::after)
			.flatMap(ac -> Stream.concat(Stream.of(ac), Stream.of(configurations)))
			.toArray(Class<?>[]::new));
	}

}
