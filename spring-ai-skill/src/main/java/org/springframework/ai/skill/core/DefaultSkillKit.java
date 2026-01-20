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

package org.springframework.ai.skill.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.skill.exception.SkillRegistrationException;
import org.springframework.ai.skill.exception.SkillValidationException;
import org.springframework.ai.skill.registration.ClassBasedSkillRegistrar;
import org.springframework.ai.skill.registration.InstanceBasedSkillRegistrar;
import org.springframework.ai.skill.support.DefaultSkillIdGenerator;
import org.springframework.ai.skill.tool.SimpleSkillLoaderTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * Default implementation of SkillKit coordinating SkillBox and SkillPoolManager.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class DefaultSkillKit implements SkillKit {

	private final SkillBox skillBox;

	private final SkillPoolManager poolManager;

	private final SkillIdGenerator idGenerator;

	private final SkillRegistrar<Class<?>> classRegistrar;

	private final SkillRegistrar<Object> instanceRegistrar;

	private final List<ToolCallback> skillLoaderTools;

	private DefaultSkillKit(Builder builder) {
		this.skillBox = Objects.requireNonNull(builder.skillBox, "skillBox cannot be null");
		this.poolManager = Objects.requireNonNull(builder.poolManager, "poolManager cannot be null");
		this.idGenerator = (builder.idGenerator != null) ? builder.idGenerator : new DefaultSkillIdGenerator();
		this.classRegistrar = (builder.classRegistrar != null) ? builder.classRegistrar
				: ClassBasedSkillRegistrar.builder().idGenerator(this.idGenerator).build();
		this.instanceRegistrar = (builder.instanceRegistrar != null) ? builder.instanceRegistrar
				: InstanceBasedSkillRegistrar.builder().idGenerator(this.idGenerator).build();
		this.skillLoaderTools = (builder.tools != null) ? builder.tools
				: List.of(ToolCallbacks.from(SimpleSkillLoaderTools.builder().skillKit(this).build()));
	}

	/**
	 * Creates a new builder instance.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public void register(SkillMetadata metadata, Supplier<Skill> loader) {
		this.validateRegistrationParameters(metadata, loader);
		String skillId = this.buildSkillId(metadata);

		if (this.poolManager.hasDefinition(skillId)) {
			throw new SkillRegistrationException(skillId, "Skill with ID '" + skillId + "' already registered. "
					+ "Each combination of (name, source) must be unique.");
		}

		SkillDefinition definition = SkillDefinition.builder()
			.skillId(skillId)
			.source(metadata.getSource())
			.loader(loader)
			.metadata(metadata)
			.build();

		this.poolManager.registerDefinition(definition);
		this.skillBox.addSkill(metadata.getName(), metadata);
	}

	// ==================== Skill Registration ====================

	@Override
	public void register(Object instance) {
		SkillDefinition definition = this.instanceRegistrar.register(this.poolManager, instance);
		this.skillBox.addSkill(definition.getMetadata().getName(), definition.getMetadata());
	}

	@Override
	public void register(Class<?> skillClass) {
		SkillDefinition definition = this.classRegistrar.register(this.poolManager, skillClass);
		this.skillBox.addSkill(definition.getMetadata().getName(), definition.getMetadata());
	}

	protected String buildSkillId(SkillMetadata metadata) {
		return this.idGenerator.generateId(metadata);
	}

	private void validateRegistrationParameters(SkillMetadata metadata, Supplier<Skill> loader) {
		if (metadata == null) {
			throw new SkillValidationException(null, "metadata cannot be null");
		}

		String name = metadata.getName();
		if (name == null || name.trim().isEmpty()) {
			throw new SkillValidationException(null, "metadata.name cannot be null or empty. "
					+ "Example: SkillMetadata.builder(\"calculator\", \"Calculator skill\", \"spring\").build()");
		}

		String description = metadata.getDescription();
		if (description == null || description.trim().isEmpty()) {
			throw new SkillValidationException(null, "metadata.description cannot be null or empty. "
					+ "Example: SkillMetadata.builder(\"calculator\", \"A skill for calculations\", \"spring\").build()");
		}

		String source = metadata.getSource();
		if (source == null || source.trim().isEmpty()) {
			throw new SkillValidationException(null,
					"metadata.source cannot be null or empty. "
							+ "Common sources: \"spring\", \"official\", \"filesystem\", \"database\". "
							+ "Example: SkillMetadata.builder(\"calculator\", \"desc\", \"spring\").build()");
		}

		if (loader == null) {
			throw new SkillValidationException(null,
					"loader cannot be null. " + "Example: skillKit.registerSkill(metadata, () -> new MySkill())");
		}
	}

	/**
	 * Gets skill instance by skillId.
	 * @param skillId unique skill ID (format: {name}_{source})
	 * @return skill instance
	 * @throws org.springframework.ai.skill.exception.SkillNotFoundException if skill not
	 * found
	 * @throws org.springframework.ai.skill.exception.SkillLoadException if loading fails
	 */
	@Override
	public Skill getSkill(String skillId) {
		return this.poolManager.load(skillId);
	}

	// ==================== Skill Access ====================

	/**
	 * Gets skill instance by name from SkillBox.
	 * @param name skill name
	 * @return skill instance, null if not found
	 */
	@Override
	public @Nullable Skill getSkillByName(String name) {
		SkillMetadata metadata = this.skillBox.getMetadata(name);
		if (metadata == null) {
			return null;
		}
		String skillId = this.buildSkillId(metadata);
		return this.poolManager.load(skillId);
	}

	/**
	 * Gets skill metadata by name from SkillBox without triggering skill loading.
	 * @param name skill name
	 * @return skill metadata, null if not found
	 */
	@Override
	public @Nullable SkillMetadata getMetadata(String name) {
		return this.skillBox.getMetadata(name);
	}

	/**
	 * Checks if skill exists in SkillBox.
	 * @param name skill name
	 * @return true if exists
	 */
	@Override
	public boolean exists(String name) {
		return this.skillBox.exists(name);
	}

	/**
	 * Adds skill metadata to SkillBox from PoolManager.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found in PoolManager or already
	 * exists in SkillBox
	 */
	public void addSkillToBox(String name) {
		SkillDefinition definition = this.findDefinitionByName(name);
		if (definition == null) {
			throw new IllegalArgumentException("Skill not found in PoolManager: " + name + ". "
					+ "Please register the skill first using registerSkill().");
		}

		this.skillBox.addSkill(name, definition.getMetadata());
	}

	// ==================== SkillBox Management ====================

	/**
	 * Finds skill definition by name, checking SkillBox sources in priority order.
	 * @param name skill name
	 * @return skill definition, null if not found
	 */
	protected @Nullable SkillDefinition findDefinitionByName(String name) {
		SkillMetadata metadata = this.skillBox.getMetadata(name);
		if (metadata != null) {
			String metadataSource = metadata.getSource();
			if (!this.skillBox.getSources().contains(metadataSource)) {
				return null;
			}

			String skillId = this.buildSkillId(metadata);
			return this.poolManager.getDefinition(skillId);
		}

		List<SkillDefinition> allDefinitions = this.poolManager.getDefinitions();

		for (String source : this.skillBox.getSources()) {
			for (SkillDefinition definition : allDefinitions) {
				SkillMetadata defMetadata = definition.getMetadata();
				if (name.equals(defMetadata.getName()) && source.equals(defMetadata.getSource())) {
					String expectedSkillId = this.buildSkillId(defMetadata);
					if (expectedSkillId.equals(definition.getSkillId())) {
						return definition;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Activates skill by name.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found in SkillBox
	 */
	@Override
	public void activateSkill(String name) {
		if (!this.skillBox.exists(name)) {
			throw new IllegalArgumentException("Skill not found in SkillBox: " + name + ". "
					+ "Please register the skill first using registerSkill().");
		}

		this.skillBox.activateSkill(name);
	}

	// ==================== Skill Activation ====================

	/**
	 * Deactivates skill by name.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found in SkillBox
	 */
	@Override
	public void deactivateSkill(String name) {
		if (!this.skillBox.exists(name)) {
			throw new IllegalArgumentException("Skill not found in SkillBox: " + name);
		}

		this.skillBox.deactivateSkill(name);
	}

	/**
	 * Deactivates all skills.
	 */
	@Override
	public void deactivateAllSkills() {
		this.skillBox.deactivateAllSkills();
	}

	/**
	 * Checks if skill is activated.
	 * @param name skill name
	 * @return true if activated
	 */
	@Override
	public boolean isActivated(String name) {
		return this.skillBox.isActivated(name);
	}

	/**
	 * Gets all tool callbacks from activated skills.
	 * @return list of tool callbacks from activated skills
	 * @throws IllegalStateException if activated skill not found in PoolManager
	 */
	@Override
	public List<ToolCallback> getAllActiveTools() {
		List<ToolCallback> allTools = new ArrayList<>();

		Set<String> activatedSkillNames = this.skillBox.getActivatedSkillNames();

		for (String name : activatedSkillNames) {
			SkillDefinition definition = this.findDefinitionByName(name);
			if (definition == null) {
				throw new IllegalStateException(
						"Skill '" + name + "' is activated in SkillBox but not found in PoolManager. "
								+ "This indicates a data inconsistency. "
								+ "Please ensure the skill is properly registered in PoolManager.");
			}

			String skillId = definition.getSkillId();
			Skill skill = this.poolManager.load(skillId);

			List<ToolCallback> tools = skill.getTools();
			if (tools != null && !tools.isEmpty()) {
				allTools.addAll(tools);
			}
		}

		return allTools;
	}

	/**
	 * Gets system prompt describing all skills in SkillBox.
	 * @return system prompt string, empty if no skills
	 */
	@Override
	public String getSkillSystemPrompt() {
		Map<String, SkillMetadata> allMetadata = this.skillBox.getAllMetadata();

		if (allMetadata.isEmpty()) {
			return "";
		}

		StringBuilder prompt = new StringBuilder();
		prompt.append("You have access to the following skills:\n\n");

		int index = 1;
		for (SkillMetadata metadata : allMetadata.values()) {
			prompt.append(String.format("%d. %s: %s\n", index++, metadata.getName(), metadata.getDescription()));
		}

		prompt.append("\n");
		prompt.append("**How to Use Skills:**\n\n");
		prompt.append(
				"1. **loadSkillContent(skillName)**: Load the full content of a skill to understand what it does and what tools it provides.\n");
		prompt.append("   - Use this when you need to know a skill's capabilities\n");
		prompt.append("   - The content will describe available tools and how to use them\n\n");
		prompt.append(
				"2. **loadSkillReference(skillName, referenceKey)**: Load a specific reference material from a skill.\n");
		prompt.append("   - ONLY use this when a skill's content EXPLICITLY mentions it has reference materials\n");
		prompt.append("   - You must use the exact reference key mentioned in the skill's content\n");
		prompt.append("   - For regular skill operations, use the skill's own tools instead\n");

		return prompt.toString();
	}

	/**
	 * Gets skill-related tool callbacks for progressive skill loading.
	 * @return list of skill tool callbacks
	 */
	@Override
	public List<ToolCallback> getSkillLoaderTools() {
		return this.skillLoaderTools;
	}

	// ==================== Skill Tools ====================

	/**
	 * Builder for DefaultSkillKit.
	 */
	public static class Builder {

		private @Nullable SkillBox skillBox;

		private @Nullable SkillPoolManager poolManager;

		private @Nullable List<ToolCallback> tools;

		private @Nullable SkillIdGenerator idGenerator;

		private @Nullable SkillRegistrar<Class<?>> classRegistrar;

		private @Nullable SkillRegistrar<Object> instanceRegistrar;

		private Builder() {
		}

		/**
		 * Sets the skill box.
		 * @param skillBox skill box instance
		 * @return this builder
		 */
		public Builder skillBox(SkillBox skillBox) {
			this.skillBox = skillBox;
			return this;
		}

		/**
		 * Sets the pool manager.
		 * @param poolManager pool manager instance
		 * @return this builder
		 */
		public Builder poolManager(SkillPoolManager poolManager) {
			this.poolManager = poolManager;
			return this;
		}

		/**
		 * Sets custom tools.
		 * @param tools tool callbacks list
		 * @return this builder
		 */
		public Builder tools(List<ToolCallback> tools) {
			this.tools = tools;
			return this;
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
		 * Sets custom class-based registrar.
		 * @param classRegistrar class registrar instance
		 * @return this builder
		 */
		public Builder classRegistrar(SkillRegistrar<Class<?>> classRegistrar) {
			this.classRegistrar = classRegistrar;
			return this;
		}

		/**
		 * Sets custom instance-based registrar.
		 * @param instanceRegistrar instance registrar instance
		 * @return this builder
		 */
		public Builder instanceRegistrar(SkillRegistrar<Object> instanceRegistrar) {
			this.instanceRegistrar = instanceRegistrar;
			return this;
		}

		/**
		 * Builds the DefaultSkillKit instance.
		 * @return new DefaultSkillKit instance
		 */
		public DefaultSkillKit build() {
			if (this.skillBox == null) {
				throw new IllegalArgumentException("skillBox cannot be null");
			}
			if (this.poolManager == null) {
				throw new IllegalArgumentException("poolManager cannot be null");
			}
			return new DefaultSkillKit(this);
		}

	}

}
