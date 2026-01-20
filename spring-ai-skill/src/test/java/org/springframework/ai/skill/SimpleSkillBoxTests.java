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

import org.junit.jupiter.api.Test;

import org.springframework.ai.skill.core.SkillMetadata;
import org.springframework.ai.skill.support.SimpleSkillBox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SimpleSkillBox}.
 *
 * @author LinPeng Zhang
 */
class SimpleSkillBoxTests {

	@Test
	void addSkillShouldStoreSkillMetadata() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata = SkillMetadata.builder("test", "description", "source").build();

		skillBox.addSkill("test", metadata);

		assertThat(skillBox.exists("test")).isTrue();
		assertThat(skillBox.getMetadata("test")).isEqualTo(metadata);
		assertThat(skillBox.getSkillCount()).isEqualTo(1);
	}

	@Test
	void addSkillShouldValidateInputs() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata = SkillMetadata.builder("test", "description", "source").build();

		assertThatThrownBy(() -> skillBox.addSkill(null, metadata)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> skillBox.addSkill("test", null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void addSkillShouldRejectDuplicateName() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata1 = SkillMetadata.builder("test", "description1", "source").build();
		SkillMetadata metadata2 = SkillMetadata.builder("test", "description2", "source").build();

		skillBox.addSkill("test", metadata1);

		assertThatThrownBy(() -> skillBox.addSkill("test", metadata2)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("already exists");
	}

	@Test
	void activationShouldToggleState() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata = SkillMetadata.builder("test", "description", "source").build();
		skillBox.addSkill("test", metadata);

		skillBox.activateSkill("test");
		assertThat(skillBox.isActivated("test")).isTrue();
		assertThat(skillBox.getActivatedSkillNames()).containsExactly("test");

		skillBox.deactivateSkill("test");
		assertThat(skillBox.isActivated("test")).isFalse();
		assertThat(skillBox.getActivatedSkillNames()).isEmpty();
	}

	@Test
	void deactivateAllSkillsShouldClearAllActivation() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata1 = SkillMetadata.builder("skill1", "description", "source").build();
		SkillMetadata metadata2 = SkillMetadata.builder("skill2", "description", "source").build();
		skillBox.addSkill("skill1", metadata1);
		skillBox.addSkill("skill2", metadata2);
		skillBox.activateSkill("skill1");
		skillBox.activateSkill("skill2");

		skillBox.deactivateAllSkills();

		assertThat(skillBox.isActivated("skill1")).isFalse();
		assertThat(skillBox.isActivated("skill2")).isFalse();
		assertThat(skillBox.getActivatedSkillNames()).isEmpty();
	}

	@Test
	void isActivatedShouldReturnFalseForNonexistentSkill() {
		SimpleSkillBox skillBox = new SimpleSkillBox();

		assertThat(skillBox.isActivated("nonexistent")).isFalse();
	}

	@Test
	void sourceManagementShouldWorkCorrectly() {
		SimpleSkillBox skillBox = new SimpleSkillBox();

		assertThat(skillBox.getSources()).containsExactly("custom");

		skillBox.setSources(List.of("source1", "source2"));
		assertThat(skillBox.getSources()).containsExactly("source1", "source2");

		skillBox.addSource("source3");
		assertThat(skillBox.getSources()).contains("source1", "source2", "source3");
	}

	@Test
	void getActivatedSkillNamesShouldReturnOnlyActiveSkills() {
		SimpleSkillBox skillBox = new SimpleSkillBox();
		SkillMetadata metadata1 = SkillMetadata.builder("skill1", "description", "source").build();
		SkillMetadata metadata2 = SkillMetadata.builder("skill2", "description", "source").build();
		SkillMetadata metadata3 = SkillMetadata.builder("skill3", "description", "source").build();
		skillBox.addSkill("skill1", metadata1);
		skillBox.addSkill("skill2", metadata2);
		skillBox.addSkill("skill3", metadata3);
		skillBox.activateSkill("skill1");
		skillBox.activateSkill("skill3");

		assertThat(skillBox.getActivatedSkillNames()).containsExactlyInAnyOrder("skill1", "skill3");
	}

}
