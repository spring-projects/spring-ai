/*
 * Copyright 2024-2024 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.moderation.*;
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
		var options = ModerationOptionsBuilder.builder().withModel("text-moderation-stable").build();

		var instructions = """
				I want to kill them.!".""";

		ModerationPrompt moderationPrompt = new ModerationPrompt(instructions, options);

		ModerationResponse moderationResponse = openAiModerationModel.call(moderationPrompt);

		assertThat(moderationResponse.getResults()).hasSize(1);

		var generation = moderationResponse.getResult();
		Moderation moderation = generation.getOutput();
		assertThat(moderation.getId()).isNotEmpty();
		assertThat(moderation.getResults()).isNotNull();
		assertThat(moderation.getResults().size()).isNotZero();
		System.out.println(moderation.getResults().toString());

		assertThat(moderation.getId()).isNotNull();
		assertThat(moderation.getModel()).isNotNull();

		ModerationResult result = moderation.getResults().get(0);
		assertThat(result.isFlagged()).isTrue();
		Categories categories = result.getCategories();
		assertThat(categories).isNotNull();
		assertThat(categories.isSexual()).isNotNull();
		assertThat(categories.isHate()).isNotNull();
		assertThat(categories.isHarassment()).isNotNull();
		assertThat(categories.isSelfHarm()).isNotNull();
		assertThat(categories.isSexualMinors()).isNotNull();
		assertThat(categories.isHateThreatening()).isNotNull();
		assertThat(categories.isViolenceGraphic()).isNotNull();
		assertThat(categories.isSelfHarmIntent()).isNotNull();
		assertThat(categories.isSelfHarmInstructions()).isNotNull();
		assertThat(categories.isHarassmentThreatening()).isNotNull();
		assertThat(categories.isViolence()).isTrue();

		CategoryScores scores = result.getCategoryScores();
		assertThat(scores.getSexual()).isNotNull();
		assertThat(scores.getHate()).isNotNull();
		assertThat(scores.getHarassment()).isNotNull();
		assertThat(scores.getSelfHarm()).isNotNull();
		assertThat(scores.getSexualMinors()).isNotNull();
		assertThat(scores.getHateThreatening()).isNotNull();
		assertThat(scores.getViolenceGraphic()).isNotNull();
		assertThat(scores.getSelfHarmIntent()).isNotNull();
		assertThat(scores.getSelfHarmInstructions()).isNotNull();
		assertThat(scores.getHarassmentThreatening()).isNotNull();
		assertThat(scores.getViolence()).isNotNull();

	}

	@Test
	void moderationAsUrlTestNegative() {
		var options = ModerationOptionsBuilder.builder().withModel("text-moderation-stable").build();

		var instructions = """
				A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

		ModerationPrompt moderationPrompt = new ModerationPrompt(instructions, options);

		ModerationResponse moderationResponse = openAiModerationModel.call(moderationPrompt);

		assertThat(moderationResponse.getResults()).hasSize(1);

		var generation = moderationResponse.getResult();
		Moderation moderation = generation.getOutput();
		assertThat(moderation.getId()).isNotEmpty();
		assertThat(moderation.getResults()).isNotNull();
		assertThat(moderation.getResults().size()).isNotZero();
		System.out.println(moderation.getResults().toString());

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
		assertThat(scores.getSexual()).isNotNull();
		assertThat(scores.getHate()).isNotNull();
		assertThat(scores.getHarassment()).isNotNull();
		assertThat(scores.getSelfHarm()).isNotNull();
		assertThat(scores.getSexualMinors()).isNotNull();
		assertThat(scores.getHateThreatening()).isNotNull();
		assertThat(scores.getViolenceGraphic()).isNotNull();
		assertThat(scores.getSelfHarmIntent()).isNotNull();
		assertThat(scores.getSelfHarmInstructions()).isNotNull();
		assertThat(scores.getHarassmentThreatening()).isNotNull();
		assertThat(scores.getViolence()).isNotNull();

	}

}
