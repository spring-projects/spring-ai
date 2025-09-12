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

package org.springframework.ai.openai.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenAiFileApi}.
 *
 * @author Sun Yuhan
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiFileApiIT {

	OpenAiFileApi fileApi = OpenAiFileApi.builder().apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY"))).build();

	@Test
	void testOperationFileCompleteProcess() throws IOException {
		String fileContent = "{\"key\":\"value\"}";
		Resource resource = new ByteArrayResource(fileContent.getBytes(StandardCharsets.UTF_8));
		String fileName = "test%s.jsonl".formatted(UUID.randomUUID().toString());
		OpenAiFileApi.Purpose purpose = OpenAiFileApi.Purpose.EVALS;

		// upload file
		OpenAiFileApi.FileObject fileObject;
		fileObject = this.fileApi
			.uploadFile(OpenAiFileApi.UploadFileRequest.builder()
				.file(toBytes(resource))
				.fileName(fileName)
				.purpose(purpose)
				.build())
			.getBody();

		assertThat(fileObject).isNotNull();
		assertThat(fileObject.filename()).isEqualTo(fileName);
		assertThat(fileObject.purpose()).isEqualTo(purpose.getValue());
		assertThat(fileObject.id()).isNotEmpty();

		// list files
		OpenAiFileApi.FileObjectResponse listFileResponse = this.fileApi
			.listFiles(OpenAiFileApi.ListFileRequest.builder().purpose(purpose).build())
			.getBody();

		assertThat(listFileResponse).isNotNull();
		assertThat(listFileResponse.data()).isNotEmpty();
		assertThat(listFileResponse.data().stream().map(OpenAiFileApi.FileObject::filename).toList())
			.contains(fileName);

		// retrieve file
		OpenAiFileApi.FileObject object = this.fileApi.retrieveFile(fileObject.id()).getBody();

		assertThat(object).isNotNull();
		assertThat(object.filename()).isEqualTo(fileName);

		// retrieve file content
		String retrieveFileContent = this.fileApi.retrieveFileContent(fileObject.id()).getBody();

		assertThat(retrieveFileContent).isNotNull();
		assertThat(retrieveFileContent).isEqualTo(fileContent);

		// delete file
		OpenAiFileApi.DeleteFileResponse deleteResponse = this.fileApi.deleteFile(fileObject.id()).getBody();

		assertThat(deleteResponse).isNotNull();
		assertThat(deleteResponse.deleted()).isEqualTo(true);
	}

	private byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

}
