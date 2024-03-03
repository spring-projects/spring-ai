package org.springframework.ai.autoconfigure.stabilityai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.*;
import org.springframework.ai.stabilityai.StyleEnum;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.stabilityai.image.api-key=" + System.getenv("STABILITYAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			ImageClient imageClient = context.getBean(ImageClient.class);
			StabilityAiImageOptions imageOptions = StabilityAiImageOptions.builder()
				.withStylePreset(StyleEnum.PHOTOGRAPHIC)
				.build();

			var instructions = """
					A light cream colored mini golden doodle.
					""";

			ImagePrompt imagePrompt = new ImagePrompt(instructions, imageOptions);
			ImageResponse imageResponse = imageClient.call(imagePrompt);

			ImageGeneration imageGeneration = imageResponse.getResult();
			Image image = imageGeneration.getOutput();

			assertThat(image.getB64Json()).isNotEmpty();
		});
	}

}
