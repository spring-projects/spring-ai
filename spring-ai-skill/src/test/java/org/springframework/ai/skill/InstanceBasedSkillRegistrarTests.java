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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.skill.core.SkillDefinition;
import org.springframework.ai.skill.core.SkillPoolManager;
import org.springframework.ai.skill.fixtures.CalculatorSkill;
import org.springframework.ai.skill.registration.InstanceBasedSkillRegistrar;
import org.springframework.ai.skill.support.DefaultSkillPoolManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InstanceBasedSkillRegistrar}.
 *
 * @author LinPeng Zhang
 */
class InstanceBasedSkillRegistrarTests {

	private InstanceBasedSkillRegistrar registrar;

	private SkillPoolManager poolManager;

	@BeforeEach
	void setUp() {
		this.registrar = InstanceBasedSkillRegistrar.builder().build();
		this.poolManager = new DefaultSkillPoolManager();
	}

	@Test
	void registerShouldRegisterValidSkillInstance() {
		CalculatorSkill skill = new CalculatorSkill();

		SkillDefinition definition = this.registrar.register(this.poolManager, skill);

		assertThat(definition).isNotNull();
		assertThat(definition.getMetadata().getName()).isEqualTo("calculator");
		assertThat(this.poolManager.hasDefinition(definition.getSkillId())).isTrue();
	}

	@Test
	void registerShouldValidateInputs() {
		assertThatThrownBy(() -> this.registrar.register(null, new CalculatorSkill()))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> this.registrar.register(this.poolManager, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> this.registrar.register(this.poolManager, "not a skill"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("@Skill");
	}

	@Test
	void supportsShouldIdentifyValidInstances() {
		assertThat(this.registrar.supports(new CalculatorSkill())).isTrue();
		assertThat(this.registrar.supports("not a skill")).isFalse();
		assertThat(this.registrar.supports(null)).isFalse();
	}

}
