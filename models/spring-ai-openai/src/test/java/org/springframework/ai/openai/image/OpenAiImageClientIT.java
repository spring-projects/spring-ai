package org.springframework.ai.openai.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageClientIT extends AbstractIT {

	@Test
	void imageAsUrlTest() {
		ImagePrompt imagePrompt = new ImagePrompt("Create an image of a mini golden doodle dog.");

		ImageResponse imageResponse = openaiImageClient.call(imagePrompt);

		System.out.println(imageResponse);

	}

}
