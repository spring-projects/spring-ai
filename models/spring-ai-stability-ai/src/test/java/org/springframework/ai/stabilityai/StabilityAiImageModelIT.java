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

package org.springframework.ai.stabilityai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StabilityAiImageTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiImageModelIT {

	@Autowired
	protected ImageModel stabilityAiImageModel;

	private static void writeFile(Image image) throws IOException {
		byte[] imageBytes = Base64.getDecoder().decode(image.getB64Json());
		String systemTempDir = System.getProperty("java.io.tmpdir");
		String filePath = systemTempDir + File.separator + "dog.png";
		File file = new File(filePath);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(imageBytes);
		}
	}

	@Test
	void imageAsBase64Test() throws IOException {

		StabilityAiImageOptions imageOptions = StabilityAiImageOptions.builder()
			.withStylePreset(StyleEnum.PHOTOGRAPHIC)
			.build();

		var instructions = """
				A light cream colored mini golden doodle.
				""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, imageOptions);

		ImageResponse imageResponse = this.stabilityAiImageModel.call(imagePrompt);

		ImageGeneration imageGeneration = imageResponse.getResult();
		Image image = imageGeneration.getOutput();

		assertThat(image.getB64Json()).isNotEmpty();

		writeFile(image);
	}

}
