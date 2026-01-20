/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.registration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.skill.adapter.SkillProxy;
import org.springframework.ai.skill.annotation.SkillContent;
import org.springframework.ai.skill.annotation.SkillInit;
import org.springframework.ai.skill.annotation.SkillTools;
import org.springframework.ai.skill.capability.SkillReferences;
import org.springframework.ai.skill.common.LoadStrategy;
import org.springframework.ai.skill.core.Skill;
import org.springframework.ai.skill.core.SkillDefinition;
import org.springframework.ai.skill.core.SkillIdGenerator;
import org.springframework.ai.skill.core.SkillMetadata;
import org.springframework.ai.skill.core.SkillPoolManager;
import org.springframework.ai.skill.core.SkillRegistrar;
import org.springframework.ai.skill.exception.SkillRegistrationException;
import org.springframework.ai.skill.support.DefaultSkillIdGenerator;

/**
 * Class-based skill registrar for lazy-loaded POJO skills.
 *
 * <p>
 * Requirements: Class must be annotated with @Skill and have a @SkillInit static factory
 * method.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class ClassBasedSkillRegistrar implements SkillRegistrar<Class<?>> {

	private final SkillIdGenerator idGenerator;

	private ClassBasedSkillRegistrar(Builder builder) {
		this.idGenerator = (builder.idGenerator != null) ? builder.idGenerator : new DefaultSkillIdGenerator();
	}

	/**
	 * Creates a new builder instance.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Finds and validates @SkillInit method.
	 * @param skillClass skill class
	 * @return validated @SkillInit method
	 * @throws IllegalArgumentException if method not found or invalid
	 */
	private static Method findAndValidateSkillInitMethod(Class<?> skillClass) {
		Method initMethod = null;

		for (Method method : skillClass.getDeclaredMethods()) {
			if (method.isAnnotationPresent(SkillInit.class)) {
				if (initMethod != null) {
					throw new IllegalArgumentException("Class " + skillClass.getName()
							+ " must have exactly one @SkillInit method, but found multiple");
				}
				initMethod = method;
			}
		}

		if (initMethod == null) {
			throw new IllegalArgumentException(
					"Class " + skillClass.getName() + " must have a @SkillInit annotated method. "
							+ "Example: @SkillInit public static MySkill create() { return new MySkill(); }");
		}

		Method method = initMethod;

		if (!Modifier.isStatic(method.getModifiers())) {
			throw new IllegalArgumentException("@SkillInit method '" + method.getName() + "' in class "
					+ skillClass.getName() + " must be static. Example: public static MySkill create() { ... }");
		}

		Class<?> returnType = method.getReturnType();
		if (!returnType.isAssignableFrom(skillClass)) {
			throw new IllegalArgumentException("@SkillInit method '" + method.getName() + "' in class "
					+ skillClass.getName() + " must return type " + skillClass.getSimpleName()
					+ " (or its superclass), but returns " + returnType.getSimpleName());
		}

		method.setAccessible(true);

		return method;
	}

	/**
	 * Builds Skill from instance.
	 * @param instance skill instance
	 * @param metadata skill metadata
	 * @return Skill instance (either direct or wrapped)
	 */
	protected static Skill buildSkillFromInstance(Object instance, SkillMetadata metadata) {
		if (instance instanceof Skill) {
			return (Skill) instance;
		}

		Map<String, Method> extensionMethods = extractExtensionMethods(instance.getClass());
		return new SkillProxy(metadata, instance, extensionMethods);
	}

	/**
	 * Extracts annotated extension methods from class.
	 * @param clazz class to scan
	 * @return extension method map
	 */
	protected static Map<String, Method> extractExtensionMethods(Class<?> clazz) {
		Map<String, Method> extensionMethods = new HashMap<>();

		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(SkillContent.class)) {
				extensionMethods.put("content", method);
			}
			if (method.isAnnotationPresent(SkillTools.class)) {
				extensionMethods.put("tools", method);
			}
			if (method.isAnnotationPresent(SkillReferences.class)) {
				extensionMethods.put("references", method);
			}
		}

		return extensionMethods;
	}

	/**
	 * Extracts extension properties from @Skill annotation.
	 * @param skillAnnotation @Skill annotation
	 * @return extension properties map
	 */
	protected static Map<String, Object> extractExtensions(
			org.springframework.ai.skill.annotation.Skill skillAnnotation) {
		Map<String, Object> extensions = new HashMap<>();

		for (String ext : skillAnnotation.extensions()) {
			String[] parts = ext.split("=", 2);
			if (parts.length == 2) {
				extensions.put(parts[0].trim(), parts[1].trim());
			}
		}

		return extensions;
	}

	/**
	 * Checks if class has @SkillInit method.
	 * @param clazz class to check
	 * @return true if at least one @SkillInit method found
	 */
	private static boolean hasSkillInitMethod(Class<?> clazz) {
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(SkillInit.class)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Registers skill from class definition.
	 * @param poolManager skill pool manager
	 * @param skillClass skill class annotated with @Skill and @SkillInit
	 * @return created skill definition
	 * @throws IllegalArgumentException if class missing required annotations
	 * @throws SkillRegistrationException if registration fails
	 */
	@Override
	public SkillDefinition register(SkillPoolManager poolManager, Class<?> skillClass) {
		Objects.requireNonNull(poolManager, "poolManager cannot be null");
		Objects.requireNonNull(skillClass, "skillClass cannot be null");

		org.springframework.ai.skill.annotation.Skill skillAnnotation = skillClass
			.getAnnotation(org.springframework.ai.skill.annotation.Skill.class);

		if (skillAnnotation == null) {
			throw new IllegalArgumentException("Class " + skillClass.getName() + " must be annotated with @Skill");
		}

		Method initMethod = findAndValidateSkillInitMethod(skillClass);

		String source = skillAnnotation.source();

		SkillMetadata metadata = SkillMetadata.builder(skillAnnotation.name(), skillAnnotation.description(), source)
			.extensions(extractExtensions(skillAnnotation))
			.build();

		String skillId = this.idGenerator.generateId(metadata);

		Supplier<Skill> loader = () -> {
			try {
				Object instance = initMethod.invoke(null);
				return buildSkillFromInstance(instance, metadata);
			}
			catch (Exception e) {
				throw new SkillRegistrationException(metadata.getName(),
						"Failed to instantiate skill class via @SkillInit method: " + skillClass.getName(), e);
			}
		};

		SkillDefinition definition = SkillDefinition.builder()
			.skillId(skillId)
			.source(source)
			.loader(loader)
			.metadata(metadata)
			.loadStrategy(LoadStrategy.LAZY)
			.build();

		poolManager.registerDefinition(definition);

		return definition;
	}

	// ==================== SkillRegistrar Interface ====================

	/**
	 * Checks if source is supported.
	 * @param source source object to check
	 * @return true if source is a Class with @Skill and @SkillInit
	 */
	@Override
	public boolean supports(Object source) {
		if (!(source instanceof Class<?> clazz)) {
			return false;
		}

		if (!clazz.isAnnotationPresent(org.springframework.ai.skill.annotation.Skill.class)) {
			return false;
		}

		return hasSkillInitMethod(clazz);
	}

	/**
	 * Builder for ClassBasedSkillRegistrar.
	 */
	public static class Builder {

		private @Nullable SkillIdGenerator idGenerator;

		private Builder() {
		}

		/**
		 * Sets custom ID generator.
		 * @param idGenerator ID generator instance
		 * @return this builder
		 */
		public Builder idGenerator(SkillIdGenerator idGenerator) {
			this.idGenerator = idGenerator;
			return this;
		}

		/**
		 * Builds the ClassBasedSkillRegistrar instance.
		 * @return new ClassBasedSkillRegistrar instance
		 */
		public ClassBasedSkillRegistrar build() {
			return new ClassBasedSkillRegistrar(this);
		}

	}

}
