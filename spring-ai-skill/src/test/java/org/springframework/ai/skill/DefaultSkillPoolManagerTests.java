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

package org.springframework.ai.skill;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.skill.common.LoadStrategy;
import org.springframework.ai.skill.core.Skill;
import org.springframework.ai.skill.core.SkillDefinition;
import org.springframework.ai.skill.core.SkillMetadata;
import org.springframework.ai.skill.exception.SkillLoadException;
import org.springframework.ai.skill.exception.SkillNotFoundException;
import org.springframework.ai.skill.support.DefaultSkillPoolManager;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultSkillPoolManager}.
 *
 * @author LinPeng Zhang
 */
class DefaultSkillPoolManagerTests {

	private DefaultSkillPoolManager poolManager;

	private SkillMetadata testMetadata;

	@BeforeEach
	void setUp() {
		this.poolManager = new DefaultSkillPoolManager();
		this.testMetadata = SkillMetadata.builder("test", "Test skill", "spring").build();
	}

	@Test
	void registerDefinitionShouldStoreDefinitionAndRejectDuplicates() {
		SkillDefinition definition = createDefinition("test_spring");

		this.poolManager.registerDefinition(definition);

		assertThat(this.poolManager.hasDefinition("test_spring")).isTrue();
		assertThat(this.poolManager.getDefinition("test_spring")).isNotNull();

		assertThatThrownBy(() -> this.poolManager.registerDefinition(definition))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void lazyLoadingShouldCacheInstancesOnFirstCall() {
		SkillDefinition definition = createDefinition("test_spring", LoadStrategy.LAZY);
		this.poolManager.registerDefinition(definition);

		Skill skill1 = this.poolManager.load("test_spring");
		Skill skill2 = this.poolManager.load("test_spring");

		assertThat(skill1).isNotNull();
		assertThat(skill1).isSameAs(skill2);
	}

	@Test
	void eagerLoadingShouldCacheInstancesImmediately() {
		SkillDefinition definition = createDefinition("test_spring", LoadStrategy.EAGER);

		this.poolManager.registerDefinition(definition);

		Skill skill1 = this.poolManager.load("test_spring");
		Skill skill2 = this.poolManager.load("test_spring");
		assertThat(skill1).isSameAs(skill2);
	}

	@Test
	void loadShouldThrowExceptionForNonexistentSkill() {
		assertThatThrownBy(() -> this.poolManager.load("nonexistent")).isInstanceOf(SkillNotFoundException.class);
	}

	@Test
	void loadShouldWrapLoaderExceptions() {
		SkillDefinition definition = SkillDefinition.builder()
			.skillId("test_spring")
			.source("spring")
			.metadata(this.testMetadata)
			.loader(() -> {
				throw new RuntimeException("Loader failed");
			})
			.build();
		this.poolManager.registerDefinition(definition);

		assertThatThrownBy(() -> this.poolManager.load("test_spring")).isInstanceOf(SkillLoadException.class)
			.hasMessageContaining("Failed to load skill");
	}

	@Test
	void evictShouldRemoveInstancesButKeepDefinitions() {
		this.poolManager.registerDefinition(createDefinition("test1_spring"));
		this.poolManager.registerDefinition(createDefinition("test2_spring"));
		Skill skill1 = this.poolManager.load("test1_spring");

		this.poolManager.evict("test1_spring");
		Skill skill1Reloaded = this.poolManager.load("test1_spring");
		assertThat(skill1).isNotSameAs(skill1Reloaded);
		assertThat(this.poolManager.hasDefinition("test1_spring")).isTrue();

		this.poolManager.evictAll();
		skill1Reloaded = this.poolManager.load("test1_spring");
		assertThat(this.poolManager.hasDefinition("test1_spring")).isTrue();
	}

	@Test
	void unregisterAndClearShouldRemoveDefinitions() {
		this.poolManager.registerDefinition(createDefinition("test1_spring"));
		this.poolManager.registerDefinition(createDefinition("test2_spring"));
		this.poolManager.load("test1_spring");

		this.poolManager.unregister("test1_spring");
		assertThat(this.poolManager.hasDefinition("test1_spring")).isFalse();
		assertThatThrownBy(() -> this.poolManager.load("test1_spring")).isInstanceOf(SkillNotFoundException.class);

		this.poolManager.clear();
		assertThat(this.poolManager.getDefinitions()).isEmpty();
	}

	@Test
	void getDefinitionsBySourceShouldFilterBySource() {
		this.poolManager.registerDefinition(createDefinition("test1_spring", "spring"));
		this.poolManager.registerDefinition(createDefinition("test2_spring", "spring"));
		this.poolManager.registerDefinition(createDefinition("test3_official", "official"));

		List<SkillDefinition> springSkills = this.poolManager.getDefinitionsBySource("spring");
		List<SkillDefinition> officialSkills = this.poolManager.getDefinitionsBySource("official");

		assertThat(springSkills).hasSize(2);
		assertThat(officialSkills).hasSize(1);
	}

	private SkillDefinition createDefinition(String skillId) {
		return createDefinition(skillId, LoadStrategy.LAZY);
	}

	private SkillDefinition createDefinition(String skillId, LoadStrategy strategy) {
		return createDefinition(skillId, "spring", strategy);
	}

	private SkillDefinition createDefinition(String skillId, String source) {
		return createDefinition(skillId, source, LoadStrategy.LAZY);
	}

	private SkillDefinition createDefinition(String skillId, String source, LoadStrategy strategy) {
		return SkillDefinition.builder()
			.skillId(skillId)
			.source(source)
			.metadata(this.testMetadata)
			.loader(() -> new TestSkill(this.testMetadata))
			.loadStrategy(strategy)
			.build();
	}

	private static class TestSkill implements Skill {

		private final SkillMetadata metadata;

		TestSkill(SkillMetadata metadata) {
			this.metadata = metadata;
		}

		@Override
		public SkillMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public String getContent() {
			return "Test content";
		}

		@Override
		public List<ToolCallback> getTools() {
			return Collections.emptyList();
		}

	}

}
