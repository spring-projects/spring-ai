package org.springframework.ai.openai.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiModerationClientTestIT extends AbstractIT {

	@Test
	void moderationTest() {
		var input = "I am a bad person";
		var moderationResponse = openAiModerationClient.call(input);
		var generation = moderationResponse.getResult();
		var moderation = generation.getOutput();
		var output = moderation.results().get(0);
		assertThat(output.flagged()).isFalse();
		assertThat(output.categoryScores().sexual()).isEqualTo(0.00010339197615394369);
		assertThat(output.categories().hate()).isFalse();
	}

}
