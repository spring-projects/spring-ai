package org.springframework.ai.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.Module;

import org.springframework.beans.BeanUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.util.ClassUtils;

/**
 * Utility methods for Jackson.
 *
 * @author Sebastien Deleuze
 */
public abstract class JacksonUtils {

	/**
	 * Instantiate well-known Jackson modules available in the classpath.
	 * <p>
	 * Supports the follow-modules: <code>Jdk8Module</code>, <code>JavaTimeModule</code>,
	 * <code>ParameterNamesModule</code> and <code>KotlinModule</code>.
	 * @return The list of instantiated modules.
	 */
	@SuppressWarnings("unchecked")
	public static List<Module> instantiateAvailableModules() {
		List<Module> modules = new ArrayList<>();
		try {
			Class<? extends com.fasterxml.jackson.databind.Module> jdk8ModuleClass = (Class<? extends Module>) ClassUtils
				.forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module", null);
			com.fasterxml.jackson.databind.Module jdk8Module = BeanUtils.instantiateClass(jdk8ModuleClass);
			modules.add(jdk8Module);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jdk8 not available
		}

		try {
			Class<? extends com.fasterxml.jackson.databind.Module> javaTimeModuleClass = (Class<? extends Module>) ClassUtils
				.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", null);
			com.fasterxml.jackson.databind.Module javaTimeModule = BeanUtils.instantiateClass(javaTimeModuleClass);
			modules.add(javaTimeModule);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jsr310 not available
		}

		try {
			Class<? extends com.fasterxml.jackson.databind.Module> parameterNamesModuleClass = (Class<? extends Module>) ClassUtils
				.forName("com.fasterxml.jackson.module.paramnames.ParameterNamesModule", null);
			com.fasterxml.jackson.databind.Module parameterNamesModule = BeanUtils
				.instantiateClass(parameterNamesModuleClass);
			modules.add(parameterNamesModule);
		}
		catch (ClassNotFoundException ex) {
			// jackson-module-parameter-names not available
		}

		// Kotlin present?
		if (KotlinDetector.isKotlinPresent()) {
			try {
				Class<? extends com.fasterxml.jackson.databind.Module> kotlinModuleClass = (Class<? extends Module>) ClassUtils
					.forName("com.fasterxml.jackson.module.kotlin.KotlinModule", null);
				Module kotlinModule = BeanUtils.instantiateClass(kotlinModuleClass);
				modules.add(kotlinModule);
			}
			catch (ClassNotFoundException ex) {
				// jackson-module-kotlin not available
			}
		}
		return modules;
	}

}
