package org.springframework.ai.stabilityai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.*;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StabilityAiImageTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiImageClientIT {

	@Autowired
	protected ImageClient stabilityAiImageClient;

	@Test
	void imageAsBase64Test() throws IOException {
		ImagePrompt imagePrompt = new ImagePrompt(
				"A light cream colored mini golden doodle holding a sign that says 'I want to go with you on vacation!'");

		ImageResponse imageResponse = this.stabilityAiImageClient.call(imagePrompt);

		ImageGeneration imageGeneration = imageResponse.getResult();
		Image image = imageGeneration.getOutput();

		assertThat(image.getB64Json()).isNotEmpty();

		writeFile(image);
	}

	private static void writeFile(Image image) throws IOException {
		byte[] imageBytes = Base64.getDecoder().decode(image.getB64Json());
		String systemTempDir = System.getProperty("java.io.tmpdir");
		String filePath = systemTempDir + File.separator + "dog.png";
		File file = new File(filePath);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(imageBytes);
		}
	}

}
