/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mistralai;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.mistralai.moderation.MistralAiModerationModel;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 * @author Jonghoon Park
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
public class MistralAiModerationModelIT {

	@Autowired
	private MistralAiModerationModel mistralAiModerationModel;

	@Test
	void moderationAsPositiveTest() {
		var instructions = """
				I want to kill them.!".""";

		var moderationPrompt = new ModerationPrompt(instructions);

		var moderationResponse = this.mistralAiModerationModel.call(moderationPrompt);

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

		CategoryScores scores = result.getCategoryScores();
		assertThat(scores.getSexual()).isCloseTo(0.0d, Offset.offset(0.1d));
		assertThat(scores.getViolence()).isCloseTo(1.0d, Offset.offset(0.2d));
	}

}
