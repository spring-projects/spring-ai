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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.skill.core.DefaultSkillKit;
import org.springframework.ai.skill.core.Skill;
import org.springframework.ai.skill.core.SkillBox;
import org.springframework.ai.skill.core.SkillMetadata;
import org.springframework.ai.skill.core.SkillPoolManager;
import org.springframework.ai.skill.exception.SkillRegistrationException;
import org.springframework.ai.skill.exception.SkillValidationException;
import org.springframework.ai.skill.fixtures.CalculatorSkill;
import org.springframework.ai.skill.fixtures.WeatherSkill;
import org.springframework.ai.skill.support.DefaultSkillPoolManager;
import org.springframework.ai.skill.support.SimpleSkillBox;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultSkillKit}.
 *
 * @author LinPeng Zhang
 */
class DefaultSkillKitTests {

	private DefaultSkillKit skillKit;

	private SkillPoolManager poolManager;

	private SkillBox skillBox;

	@BeforeEach
	void setUp() {
		this.poolManager = new DefaultSkillPoolManager();
		SimpleSkillBox simpleSkillBox = new SimpleSkillBox();
		simpleSkillBox.setSources(List.of("custom", "example"));
		this.skillBox = simpleSkillBox;
		this.skillKit = DefaultSkillKit.builder().skillBox(this.skillBox).poolManager(this.poolManager).build();
	}

	@Test
	void registerShouldValidateInputsAndRejectDuplicates() {
		SkillMetadata metadata = SkillMetadata.builder("test", "description", "custom").build();

		assertThatThrownBy(() -> this.skillKit.register(null, () -> new TestSkill(metadata)))
			.isInstanceOf(SkillValidationException.class)
			.hasMessageContaining("metadata cannot be null");
		assertThatThrownBy(() -> this.skillKit.register(metadata, null)).isInstanceOf(SkillValidationException.class)
			.hasMessageContaining("loader cannot be null");

		this.skillKit.register(metadata, () -> new TestSkill(metadata));
		assertThatThrownBy(() -> this.skillKit.register(metadata, () -> new TestSkill(metadata)))
			.isInstanceOf(SkillRegistrationException.class)
			.hasMessageContaining("already registered");
	}

	@Test
	void registerFromInstanceShouldRegisterAnnotatedSkill() {
		this.skillKit.register(new CalculatorSkill());

		assertThat(this.skillKit.exists("calculator")).isTrue();
		assertThat(this.skillKit.getMetadata("calculator")).isNotNull();
	}

	@Test
	void registerFromClassShouldRegisterClass() {
		this.skillKit.register(WeatherSkill.class);

		assertThat(this.skillKit.exists("weather")).isTrue();
		Skill skill = this.skillKit.getSkillByName("weather");
		assertThat(skill).isNotNull();
		assertThat(skill.getTools()).isNotEmpty();
	}

	@Test
	void deactivateAllSkillsShouldClearAllActivation() {
		this.skillKit.register(new CalculatorSkill());
		this.skillKit.register(WeatherSkill.class);
		this.skillKit.activateSkill("calculator");
		this.skillKit.activateSkill("weather");

		this.skillKit.deactivateAllSkills();

		assertThat(this.skillKit.isActivated("calculator")).isFalse();
		assertThat(this.skillKit.isActivated("weather")).isFalse();
	}

	@Test
	void getSkillByNameShouldReturnSkillOrNull() {
		this.skillKit.register(new CalculatorSkill());

		Skill skill = this.skillKit.getSkillByName("calculator");
		assertThat(skill).isNotNull();
		assertThat(skill.getMetadata().getName()).isEqualTo("calculator");

		Skill nonexistent = this.skillKit.getSkillByName("nonexistent");
		assertThat(nonexistent).isNull();
	}

	@Test
	void getAllActiveToolsShouldReturnToolsFromActivatedSkills() {
		this.skillKit.register(WeatherSkill.class);
		this.skillKit.activateSkill("weather");

		List<ToolCallback> tools = this.skillKit.getAllActiveTools();

		assertThat(tools).isNotEmpty();
	}

	@Test
	void getSkillSystemPromptShouldGeneratePromptContent() {
		String emptyPrompt = this.skillKit.getSkillSystemPrompt();
		assertThat(emptyPrompt).isEmpty();

		this.skillKit.register(new CalculatorSkill());
		String prompt = this.skillKit.getSkillSystemPrompt();
		assertThat(prompt).contains("calculator").contains("loadSkillContent");
	}

	@Test
	void multiTenantShouldIsolateBoxButSharePoolManager() {
		SkillBox tenant1Box = new SimpleSkillBox();
		SkillBox tenant2Box = new SimpleSkillBox();
		SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

		DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
			.skillBox(tenant1Box)
			.poolManager(sharedPoolManager)
			.build();
		DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
			.skillBox(tenant2Box)
			.poolManager(sharedPoolManager)
			.build();

		tenant1Kit.register(new CalculatorSkill());

		assertThat(tenant1Kit.exists("calculator")).isTrue();
		assertThat(tenant2Kit.exists("calculator")).isFalse();
	}

	@Test
	void multiTenantShouldIsolateActivationState() {
		SimpleSkillBox tenant1Box = new SimpleSkillBox();
		tenant1Box.setSources(List.of("example"));
		SimpleSkillBox tenant2Box = new SimpleSkillBox();
		tenant2Box.setSources(List.of("example"));
		SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

		DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
			.skillBox(tenant1Box)
			.poolManager(sharedPoolManager)
			.build();
		DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
			.skillBox(tenant2Box)
			.poolManager(sharedPoolManager)
			.build();

		tenant1Kit.register(new CalculatorSkill());
		tenant1Kit.activateSkill("calculator");

		tenant2Kit.addSkillToBox("calculator");

		assertThat(tenant1Kit.isActivated("calculator")).isTrue();
		assertThat(tenant2Kit.isActivated("calculator")).isFalse();
	}

	@Test
	void multiTenantShouldShareSingletonInstances() {
		SimpleSkillBox tenant1Box = new SimpleSkillBox();
		tenant1Box.setSources(List.of("example"));
		SimpleSkillBox tenant2Box = new SimpleSkillBox();
		tenant2Box.setSources(List.of("example"));
		SkillPoolManager sharedPoolManager = new DefaultSkillPoolManager();

		DefaultSkillKit tenant1Kit = DefaultSkillKit.builder()
			.skillBox(tenant1Box)
			.poolManager(sharedPoolManager)
			.build();
		DefaultSkillKit tenant2Kit = DefaultSkillKit.builder()
			.skillBox(tenant2Box)
			.poolManager(sharedPoolManager)
			.build();

		tenant1Kit.register(new CalculatorSkill());
		tenant2Kit.addSkillToBox("calculator");

		Skill skill1 = tenant1Kit.getSkillByName("calculator");
		Skill skill2 = tenant2Kit.getSkillByName("calculator");

		assertThat(skill1).isSameAs(skill2);
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
			return List.of();
		}

	}

}
