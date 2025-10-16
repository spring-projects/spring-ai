package org.springframework.ai.utils;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

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
 * @see AutoConfigurations
 */
public class SpringAiTestAutoConfigurations {

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
