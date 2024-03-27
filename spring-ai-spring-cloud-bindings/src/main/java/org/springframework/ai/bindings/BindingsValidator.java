package org.springframework.ai.bindings;

import org.springframework.core.env.Environment;

/**
 * From https://github.com/spring-cloud/spring-cloud-bindings to switch on/off the
 * bindings.
 */
final class BindingsValidator {

	static final String CONFIG_PATH = "spring.ai.cloud.bindings";

	/**
	 * Whether the given binding type should be used to contribute properties.
	 */
	static boolean isTypeEnabled(Environment environment, String type) {
		return environment.getProperty("%s.%s.enabled".formatted(CONFIG_PATH, type), Boolean.class, true);
	}

}
