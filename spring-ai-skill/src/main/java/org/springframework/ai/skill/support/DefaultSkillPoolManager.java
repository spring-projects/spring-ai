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

package org.springframework.ai.skill.support;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.skill.common.LoadStrategy;
import org.springframework.ai.skill.core.Skill;
import org.springframework.ai.skill.core.SkillDefinition;
import org.springframework.ai.skill.core.SkillPoolManager;
import org.springframework.ai.skill.exception.SkillLoadException;
import org.springframework.ai.skill.exception.SkillNotFoundException;

/**
 * Default implementation of SkillPoolManager with thread-safe caching.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class DefaultSkillPoolManager implements SkillPoolManager {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSkillPoolManager.class);

	private final ConcurrentHashMap<String, SkillDefinition> definitions = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Skill> skillPool = new ConcurrentHashMap<>();

	@Override
	public void registerDefinition(SkillDefinition definition) {
		Objects.requireNonNull(definition, "definition cannot be null");

		String skillId = definition.getSkillId();

		if (this.definitions.containsKey(skillId)) {
			throw new IllegalArgumentException("Skill with ID '" + skillId + "' is already registered");
		}

		this.definitions.put(skillId, definition);

		if (definition.getLoadStrategy() == LoadStrategy.EAGER) {
			try {
				Skill skill = this.createInstance(definition);
				this.skillPool.put(skillId, skill);
			}
			catch (Exception e) {
				this.definitions.remove(skillId);
				throw new SkillLoadException(skillId, "EAGER loading failed during registration", e);
			}
		}
	}

	@Override
	public @Nullable SkillDefinition getDefinition(String skillId) {
		return this.definitions.get(skillId);
	}

	@Override
	public boolean hasDefinition(String skillId) {
		return this.definitions.containsKey(skillId);
	}

	@Override
	public Skill load(String skillId) {
		Objects.requireNonNull(skillId, "skillId cannot be null");

		SkillDefinition definition = this.definitions.get(skillId);
		if (definition == null) {
			throw new SkillNotFoundException(skillId);
		}

		return this.doLoad(skillId, definition);
	}

	private Skill doLoad(String skillId, SkillDefinition definition) {
		return this.skillPool.computeIfAbsent(skillId, id -> this.createInstance(definition));
	}

	private Skill createInstance(SkillDefinition definition) {
		try {
			Skill skill = definition.getLoader().get();
			if (skill == null) {
				throw new NullPointerException("Loader returned null");
			}
			return skill;
		}
		catch (Exception e) {
			throw new SkillLoadException(definition.getSkillId(), "Loader threw exception", e);
		}
	}

	@Override
	public List<SkillDefinition> getDefinitions() {
		return List.copyOf(this.definitions.values());
	}

	/**
	 * Gets skill definitions by source.
	 * @param source source identifier
	 * @return list of skill definitions
	 */
	public List<SkillDefinition> getDefinitionsBySource(String source) {
		Objects.requireNonNull(source, "source cannot be null");

		return this.definitions.values()
			.stream()
			.filter(def -> source.equals(def.getSource()))
			.collect(Collectors.toList());
	}

	/**
	 * Gets skill definitions by name.
	 * @param name skill name
	 * @return list of skill definitions
	 */
	public List<SkillDefinition> getDefinitionsByName(String name) {
		Objects.requireNonNull(name, "name cannot be null");

		return this.definitions.values()
			.stream()
			.filter(def -> name.equals(def.getMetadata().getName()))
			.collect(Collectors.toList());
	}

	@Override
	public void evict(String skillId) {
		this.skillPool.remove(skillId);
	}

	@Override
	public void evictAll() {
		this.skillPool.clear();
	}

	@Override
	public void unregister(String skillId) {
		Objects.requireNonNull(skillId, "skillId cannot be null");

		this.skillPool.remove(skillId);

		SkillDefinition removedDefinition = this.definitions.remove(skillId);
		if (removedDefinition == null) {
			logger.warn("Attempted to unregister non-existent skill: {}", skillId);
		}
	}

	@Override
	public void clear() {
		this.definitions.clear();
		this.skillPool.clear();
	}

}
