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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.core.annotation.AnnotationUtils;

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
 * @since 1.1.0
 * @see AutoConfigurations
 */
public final class SpringAiTestAutoConfigurations {

	private SpringAiTestAutoConfigurations() {
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
	 */
	public static AutoConfigurations of(Class<?>... configurations) {
		return AutoConfigurations.of(Arrays.stream(configurations)
			.map(c -> AnnotationUtils.findAnnotation(c, AutoConfiguration.class))
			.filter(Objects::nonNull)
			.map(AutoConfiguration::after)
			.flatMap(ac -> Stream.concat(Stream.of(ac), Stream.of(configurations)))
			.toArray(Class<?>[]::new));
	}

}
