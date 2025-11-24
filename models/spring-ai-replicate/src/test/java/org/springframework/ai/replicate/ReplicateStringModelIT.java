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

package org.springframework.ai.replicate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.replicate.ReplicateStringModel.StringResponse;
import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.ai.replicate.api.ReplicateApi.FileUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicateStringModel}.
 *
 * @author Rene Maierhofer
 */
@SpringBootTest(classes = ReplicateTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "REPLICATE_API_TOKEN", matches = ".+")
class ReplicateStringModelIT {

	@Autowired
	private ReplicateStringModel stringModel;

	@Autowired
	private ReplicateApi replicateApi;

	@Test
	void testClassifyImageWithFileUpload() {
		Path imagePath = Paths.get("src/test/resources/test-image.jpg");
		FileSystemResource fileResource = new FileSystemResource(imagePath);

		FileUploadResponse uploadResponse = this.replicateApi.uploadFile(fileResource, "test-image.jpg");

		assertThat(uploadResponse).isNotNull();
		assertThat(uploadResponse.urls()).isNotNull();
		assertThat(uploadResponse.urls().get()).isNotEmpty();

		String imageUrl = uploadResponse.urls().get();

		ReplicateOptions options = ReplicateOptions.builder()
			.model("falcons-ai/nsfw_image_detection")
			.withParameter("image", imageUrl)
			.build();

		StringResponse response = this.stringModel.generate(options);

		// Validate output
		assertThat(response).isNotNull();
		assertThat(response.getOutput()).isNotNull().isInstanceOf(String.class).isNotEmpty();
		assertThat(response.getOutput().toLowerCase()).isEqualTo("normal");

		// Validate metadata
		assertThat(response.getPredictionResponse()).isNotNull();
		assertThat(response.getPredictionResponse().id()).isNotNull();
	}

	@Test
	void testClassifyImageWithBase64() throws IOException {
		Path imagePath = Paths.get("src/test/resources/test-image.jpg");
		byte[] imageBytes = Files.readAllBytes(imagePath);
		String base64Image = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(imageBytes);

		ReplicateOptions options = ReplicateOptions.builder()
			.model("falcons-ai/nsfw_image_detection")
			.withParameter("image", base64Image)
			.build();

		StringResponse response = this.stringModel.generate(options);

		assertThat(response).isNotNull();
		assertThat(response.getOutput()).isNotNull().isInstanceOf(String.class).isNotEmpty();
		assertThat(response.getOutput().toLowerCase()).isEqualTo("normal");
		assertThat(response.getPredictionResponse()).isNotNull();
		assertThat(response.getPredictionResponse().id()).isNotNull();
	}

}
