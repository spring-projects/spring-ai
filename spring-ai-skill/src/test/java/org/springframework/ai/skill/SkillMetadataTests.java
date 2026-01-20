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

import org.junit.jupiter.api.Test;

import org.springframework.ai.skill.core.SkillMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SkillMetadata}.
 *
 * @author LinPeng Zhang
 */
class SkillMetadataTests {

	@Test
	void builderShouldCreateMetadataWithRequiredFields() {
		SkillMetadata metadata = SkillMetadata.builder("test-skill", "Test description", "spring").build();

		assertThat(metadata.getName()).isEqualTo("test-skill");
		assertThat(metadata.getDescription()).isEqualTo("Test description");
		assertThat(metadata.getSource()).isEqualTo("spring");
		assertThat(metadata.getExtensions()).isEmpty();
	}

	@Test
	void builderShouldStoreExtensions() {
		SkillMetadata metadata = SkillMetadata.builder("skill", "description", "spring")
			.extension("version", "1.0.0")
			.extension("author", "test")
			.build();

		assertThat(metadata.getExtension("version")).isEqualTo("1.0.0");
		assertThat(metadata.getExtension("author")).isEqualTo("test");
		assertThat(metadata.getExtension("nonexistent")).isNull();
	}

	@Test
	void builderShouldValidateRequiredFields() {
		assertThatThrownBy(() -> SkillMetadata.builder(null, "description", "spring").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillMetadata.builder("", "description", "spring").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillMetadata.builder("name", null, "spring").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillMetadata.builder("name", "", "spring").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillMetadata.builder("name", "description", null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SkillMetadata.builder("name", "description", "").build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void equalsShouldWorkCorrectly() {
		SkillMetadata metadata1 = SkillMetadata.builder("name", "description", "spring")
			.extension("key", "value")
			.build();
		SkillMetadata metadata2 = SkillMetadata.builder("name", "description", "spring")
			.extension("key", "value")
			.build();
		SkillMetadata metadata3 = SkillMetadata.builder("different", "description", "spring").build();

		assertThat(metadata1).isEqualTo(metadata2).hasSameHashCodeAs(metadata2);
		assertThat(metadata1).isNotEqualTo(metadata3);
	}

}
