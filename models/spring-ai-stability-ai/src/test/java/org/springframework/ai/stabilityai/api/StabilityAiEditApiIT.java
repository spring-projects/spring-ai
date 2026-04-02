/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.stabilityai.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.ImageEditHeaders;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.ImageEditHeaders.AcceptType;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.StructuredResponse;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.RemoveBackgroundRequest;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.RemoveBackgroundRequest.OutputFormat;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for StabilityAiEditApi.
 *
 * @author inpink
 */
@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiEditApiIT {

	StabilityAiEditApi editApi = new StabilityAiEditApi(System.getenv("STABILITYAI_API_KEY"));

	@Test
	void removeBackgroundToRawImage() throws IOException {
		// given
		File inputImage = new File("src/test/resources/test.png");
		byte[] imageBytes = Files.readAllBytes(inputImage.toPath());

		RemoveBackgroundRequest request = RemoveBackgroundRequest.builder().image(imageBytes).build();

		// when
		byte[] response = (byte[]) editApi.removeBackground(request, byte[].class).getBody();

		// then
		assertThat(response).isNotNull();

		writeRawImageToFile(response, "png");
	}

	@Test
	void removeBackgroundToBase64Image() throws IOException {
		// given
		File inputImage = new File("src/test/resources/test.png");
		byte[] imageBytes = Files.readAllBytes(inputImage.toPath());

		RemoveBackgroundRequest request = RemoveBackgroundRequest.builder()
			.image(imageBytes)
			.outputFormat(OutputFormat.WEBP)
			.build();

		ImageEditHeaders headers = ImageEditHeaders.builder()
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.acceptType(AcceptType.JSON)
			.stabilityClientId("my-awesome-app")
			.stabilityClientUserId("DiscordUser#9999")
			.stabilityClientVersion("1.2.1")
			.build();

		// when
		StructuredResponse response = editApi.removeBackground(request, headers, StructuredResponse.class).getBody();

		// then
		assertThat(response).isNotNull();
		assertThat(response.b64Image()).isNotNull();
		assertThat(response.seed()).isNotNull();
		assertThat(response.finishReason()).isEqualTo("SUCCESS");

		writeBase64ImagetoFile(response.b64Image(), "webp");
	}

	private void writeRawImageToFile(byte[] imageData, String format) throws IOException {
		String systemTempDir = System.getProperty("java.io.tmpdir");
		String fileName = "output_image." + format;
		String filePath = systemTempDir + File.separator + fileName;
		File file = new File(filePath);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(imageData);
		}
		System.out.println("Image saved to: " + filePath);
	}

	private void writeBase64ImagetoFile(String base64Image, String format) throws IOException {
		byte[] decodedImage = Base64.getDecoder().decode(base64Image);
		writeRawImageToFile(decodedImage, format);
	}

}
