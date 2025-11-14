/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.moderation;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationOptionsBuilder;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ahmed Yousri
 * @since 0.9.0
 */

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiModerationModelIT extends AbstractIT {

	@Test
	void moderationAsUrlTestPositive() {
		var options = ModerationOptionsBuilder.builder().model("omni-moderation-latest").build();

		var instructions = """
				I want to kill them.!".""";

		ModerationPrompt moderationPrompt = new ModerationPrompt(instructions, options);

		ModerationResponse moderationResponse = this.openAiModerationModel.call(moderationPrompt);

		assertThat(moderationResponse.getResults()).hasSize(1);

		var generation = moderationResponse.getResult();
		Moderation moderation = generation.getOutput();
		assertThat(moderation.getId()).isNotEmpty();
		assertThat(moderation.getResults()).isNotNull();
		assertThat(moderation.getResults().size()).isNotZero();

		assertThat(moderation.getId()).isNotNull();
		assertThat(moderation.getModel()).isNotNull();

		ModerationResult result = moderation.getResults().get(0);
		assertThat(result.isFlagged()).isTrue();
		Categories categories = result.getCategories();
		assertThat(categories).isNotNull();
		assertThat(categories.isViolence()).isTrue();

		CategoryScores scores = result.getCategoryScores();
		assertThat(scores.getSexual()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getViolence()).isCloseTo(1.0d, Offset.offset(0.2d));

	}

	@Test
	void moderationAsUrlTestNegative() {
		var options = ModerationOptionsBuilder.builder().model("omni-moderation-latest").build();

		var instructions = """
				A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

		ModerationPrompt moderationPrompt = new ModerationPrompt(instructions, options);

		ModerationResponse moderationResponse = this.openAiModerationModel.call(moderationPrompt);

		assertThat(moderationResponse.getResults()).hasSize(1);

		var generation = moderationResponse.getResult();
		Moderation moderation = generation.getOutput();
		assertThat(moderation.getId()).isNotEmpty();
		assertThat(moderation.getResults()).isNotNull();
		assertThat(moderation.getResults().size()).isNotZero();

		assertThat(moderation.getId()).isNotNull();
		assertThat(moderation.getModel()).isNotNull();

		ModerationResult result = moderation.getResults().get(0);
		assertThat(result.isFlagged()).isFalse();
		Categories categories = result.getCategories();
		assertThat(categories.isSexual()).isFalse();
		assertThat(categories.isHate()).isFalse();
		assertThat(categories.isHarassment()).isFalse();
		assertThat(categories.isSelfHarm()).isFalse();
		assertThat(categories.isSexualMinors()).isFalse();
		assertThat(categories.isHateThreatening()).isFalse();
		assertThat(categories.isViolenceGraphic()).isFalse();
		assertThat(categories.isSelfHarmIntent()).isFalse();
		assertThat(categories.isSelfHarmInstructions()).isFalse();
		assertThat(categories.isHarassmentThreatening()).isFalse();
		assertThat(categories.isViolence()).isFalse();

		CategoryScores scores = result.getCategoryScores();
		assertThat(scores.getSexual()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getHate()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getHarassment()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getSelfHarm()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getSexualMinors()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getHateThreatening()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getViolenceGraphic()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getSelfHarmIntent()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getSelfHarmInstructions()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getHarassmentThreatening()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getViolence()).isCloseTo(0.0d, Offset.offset(0.1d));

	}

}
