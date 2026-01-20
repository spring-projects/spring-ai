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

/**
 * The {@code org.springframework.ai.skill} package provides classes and interfaces for
 * managing and executing skills (modular AI capabilities) in Spring AI applications. It
 * includes core components for skill registration, lifecycle management, and progressive
 * loading.
 *
 * <p>
 * Key classes and interfaces:
 * </p>
 * <ul>
 * <li>{@link org.springframework.ai.skill.core.SkillKit} - Main entry point for skill
 * management, coordinating {@link org.springframework.ai.skill.core.SkillBox} and
 * {@link org.springframework.ai.skill.core.SkillPoolManager}.</li>
 * <li>{@link org.springframework.ai.skill.core.DefaultSkillKit} - Default implementation
 * of {@link org.springframework.ai.skill.core.SkillKit}.</li>
 * <li>{@link org.springframework.ai.skill.core.SkillBox} - Container for managing skill
 * metadata and activation state.</li>
 * <li>{@link org.springframework.ai.skill.support.SimpleSkillBox} - Default
 * implementation of {@link org.springframework.ai.skill.core.SkillBox}.</li>
 * <li>{@link org.springframework.ai.skill.core.SkillPoolManager} - Manages skill
 * instances, definitions, and caching strategies.</li>
 * <li>{@link org.springframework.ai.skill.support.DefaultSkillPoolManager} - Default
 * implementation of {@link org.springframework.ai.skill.core.SkillPoolManager}.</li>
 * <li>{@link org.springframework.ai.skill.core.Skill} - Core interface representing an
 * executable skill with metadata, content, and tools.</li>
 * <li>{@link org.springframework.ai.skill.core.SkillMetadata} - Immutable metadata
 * describing a skill's name, description, source, and extensions.</li>
 * <li>{@link org.springframework.ai.skill.core.SkillDefinition} - Defines how a skill
 * should be loaded and managed.</li>
 * <li>{@link org.springframework.ai.skill.core.SkillRegistrar} - SPI for pluggable skill
 * registration strategies.</li>
 * <li>{@link org.springframework.ai.skill.registration.ClassBasedSkillRegistrar} -
 * Registers skills from annotated classes with {@code @SkillInit} factory methods.</li>
 * <li>{@link org.springframework.ai.skill.registration.InstanceBasedSkillRegistrar} -
 * Registers skills from pre-created instances annotated with {@code @Skill}.</li>
 * </ul>
 *
 * <p>
 * Spring AI integration classes:
 * </p>
 * <ul>
 * <li>{@link org.springframework.ai.skill.spi.SkillAwareAdvisor} - Chat advisor for
 * injecting skill system prompts and managing skill lifecycle.</li>
 * <li>{@link org.springframework.ai.skill.spi.SkillAwareToolCallbackResolver} - Resolves
 * tool callbacks dynamically from active skills.</li>
 * <li>{@link org.springframework.ai.skill.spi.SkillAwareToolCallingManager} - Manages
 * tool calling with skill-aware tool resolution.</li>
 * <li>{@link org.springframework.ai.skill.tool.SimpleSkillLoaderTools} - Provides
 * {@code loadSkillContent} and {@code loadSkillReference} tools for progressive skill
 * loading.</li>
 * </ul>
 *
 * <p>
 * Annotation-driven development:
 * </p>
 * <ul>
 * <li>{@link org.springframework.ai.skill.annotation.Skill} - Marks a class as a skill
 * with metadata.</li>
 * <li>{@link org.springframework.ai.skill.annotation.SkillInit} - Marks a static factory
 * method for lazy skill instantiation.</li>
 * <li>{@link org.springframework.ai.skill.annotation.SkillContent} - Marks a method that
 * provides skill documentation.</li>
 * <li>{@link org.springframework.ai.skill.annotation.SkillTools} - Marks a method that
 * provides skill tool callbacks.</li>
 * </ul>
 *
 * <p>
 * Exception hierarchy:
 * </p>
 * <ul>
 * <li>{@link org.springframework.ai.skill.exception.SkillException} - Base exception for
 * all skill-related errors.</li>
 * <li>{@link org.springframework.ai.skill.exception.SkillNotFoundException} - Thrown when
 * a requested skill cannot be found.</li>
 * <li>{@link org.springframework.ai.skill.exception.SkillLoadException} - Thrown when a
 * skill fails to load.</li>
 * <li>{@link org.springframework.ai.skill.exception.SkillRegistrationException} - Thrown
 * when skill registration fails.</li>
 * <li>{@link org.springframework.ai.skill.exception.SkillValidationException} - Thrown
 * when skill validation fails.</li>
 * </ul>
 */
@NullMarked
package org.springframework.ai.skill;

import org.jspecify.annotations.NullMarked;
