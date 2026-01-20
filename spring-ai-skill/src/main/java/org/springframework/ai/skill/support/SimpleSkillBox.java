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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.skill.core.SkillBox;
import org.springframework.ai.skill.core.SkillMetadata;

/**
 * Default implementation of SkillBox with thread-safe metadata storage.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class SimpleSkillBox implements SkillBox {

	private static final Logger logger = LoggerFactory.getLogger(SimpleSkillBox.class);

	private final ConcurrentHashMap<String, SkillMetadata> skills = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Boolean> activated = new ConcurrentHashMap<>();

	private final List<String> sources = new ArrayList<>();

	public SimpleSkillBox() {
		this.sources.add("custom");
	}

	@Override
	public void addSkill(String name, SkillMetadata metadata) {
		Objects.requireNonNull(name, "name cannot be null");
		Objects.requireNonNull(metadata, "metadata cannot be null");

		if (this.skills.containsKey(name)) {
			throw new IllegalArgumentException("Skill with name '" + name + "' already exists in this SkillBox");
		}

		this.skills.put(name, metadata);
		this.addSource(metadata.getSource());
		this.activated.putIfAbsent(name, false);
	}

	@Override
	public @Nullable SkillMetadata getMetadata(String name) {
		return this.skills.get(name);
	}

	@Override
	public Map<String, SkillMetadata> getAllMetadata() {
		return Collections.unmodifiableMap(this.skills);
	}

	@Override
	public boolean exists(String name) {
		return this.skills.containsKey(name);
	}

	@Override
	public int getSkillCount() {
		return this.skills.size();
	}

	@Override
	public void activateSkill(String name) {
		this.activated.put(name, true);
	}

	@Override
	public void deactivateSkill(String name) {
		this.activated.put(name, false);
	}

	@Override
	public void deactivateAllSkills() {
		this.activated.replaceAll((k, v) -> false);
	}

	@Override
	public boolean isActivated(String name) {
		return this.activated.getOrDefault(name, false);
	}

	@Override
	public Set<String> getActivatedSkillNames() {
		return this.activated.entrySet()
			.stream()
			.filter(Map.Entry::getValue)
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	@Override
	public List<String> getSources() {
		return this.sources;
	}

	public void setSources(List<String> sourceList) {
		this.sources.clear();
		this.sources.addAll(sourceList);
	}

	public void addSource(String source) {
		if (!this.sources.contains(source)) {
			this.sources.add(source);
		}
	}

}
