package org.springframework.ai.huggingface.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.huggingface.HuggingfaceImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_TEXT_TO_IMAGE_URL", matches = ".+")
public class TextToImageClientIT {

	@Autowired
	protected HuggingfaceImageModel huggingfaceImageModel;

	@Test
	void helloWorldCompletion() {
		String textToImageInstruct = """
				A cat touching a mirror and seeing its reflection for the first time.
				""";
		ImagePrompt prompt = new ImagePrompt(textToImageInstruct);
		ImageResponse response = huggingfaceImageModel.call(prompt);
		assertThat(response.getResult().getOutput()).isNotNull();
		assertThat(response.getResult().getMetadata()).isNotNull();
		assertThat(response.getResult().getOutput().getB64Json()).isNotEmpty();
	}

}
