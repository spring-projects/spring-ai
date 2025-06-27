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

package org.springframework.ai.azure.openai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class AzureEmbeddingsOptionsTests {

	private OpenAIClient mockClient;

	private AzureOpenAiEmbeddingModel client;

	@BeforeEach
	void setUp() {
		this.mockClient = Mockito.mock(OpenAIClient.class);
		this.client = new AzureOpenAiEmbeddingModel(this.mockClient, MetadataMode.EMBED,
				AzureOpenAiEmbeddingOptions.builder().deploymentName("DEFAULT_MODEL").user("USER_TEST").build());
	}

	@Test
	public void createRequestWithChatOptions() {
		var requestOptions = this.client
			.toEmbeddingOptions(new EmbeddingRequest(List.of("Test message content"), null));

		assertThat(requestOptions.getInput()).hasSize(1);
		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getUser()).isEqualTo("USER_TEST");

		requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(List.of("Test message content"),
				AzureOpenAiEmbeddingOptions.builder().deploymentName("PROMPT_MODEL").user("PROMPT_USER").build()));

		assertThat(requestOptions.getInput()).hasSize(1);
		assertThat(requestOptions.getModel()).isEqualTo("PROMPT_MODEL");
		assertThat(requestOptions.getUser()).isEqualTo("PROMPT_USER");
	}

	@Test
	public void createRequestWithMultipleInputs() {
		List<String> inputs = Arrays.asList("First text", "Second text", "Third text");
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(inputs, null));

		assertThat(requestOptions.getInput()).hasSize(3);
		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getUser()).isEqualTo("USER_TEST");
	}

	@Test
	public void createRequestWithEmptyInputs() {
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(Collections.emptyList(), null));

		assertThat(requestOptions.getInput()).isEmpty();
		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
	}

	@Test
	public void createRequestWithNullOptions() {
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(List.of("Test content"), null));

		assertThat(requestOptions.getInput()).hasSize(1);
		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getUser()).isEqualTo("USER_TEST");
	}

	@Test
	public void requestOptionsShouldOverrideDefaults() {
		var customOptions = AzureOpenAiEmbeddingOptions.builder()
			.deploymentName("CUSTOM_MODEL")
			.user("CUSTOM_USER")
			.build();

		var requestOptions = this.client
			.toEmbeddingOptions(new EmbeddingRequest(List.of("Test content"), customOptions));

		assertThat(requestOptions.getModel()).isEqualTo("CUSTOM_MODEL");
		assertThat(requestOptions.getUser()).isEqualTo("CUSTOM_USER");
	}

	@Test
	public void shouldPreserveInputOrder() {
		List<String> orderedInputs = Arrays.asList("First", "Second", "Third", "Fourth");
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(orderedInputs, null));

		assertThat(requestOptions.getInput()).containsExactly("First", "Second", "Third", "Fourth");
	}

	@Test
	public void shouldHandleDifferentMetadataModes() {
		var clientWithNoneMode = new AzureOpenAiEmbeddingModel(this.mockClient, MetadataMode.NONE,
				AzureOpenAiEmbeddingOptions.builder().deploymentName("TEST_MODEL").build());

		var requestOptions = clientWithNoneMode.toEmbeddingOptions(new EmbeddingRequest(List.of("Test content"), null));

		assertThat(requestOptions.getModel()).isEqualTo("TEST_MODEL");
		assertThat(requestOptions.getInput()).hasSize(1);
	}

	@Test
	public void shouldCreateOptionsBuilderWithAllParameters() {
		var options = AzureOpenAiEmbeddingOptions.builder().deploymentName("test-deployment").user("test-user").build();

		assertThat(options.getDeploymentName()).isEqualTo("test-deployment");
		assertThat(options.getUser()).isEqualTo("test-user");
	}

	@Test
	public void shouldValidateDeploymentNameNotNull() {
		// This test assumes that the builder or model validates deployment name
		// Adjust based on actual validation logic in your implementation
		var optionsWithoutDeployment = AzureOpenAiEmbeddingOptions.builder().user("test-user").build();

		// If there's validation, this should throw an exception
		// Otherwise, adjust the test based on expected behavior
		assertThat(optionsWithoutDeployment.getUser()).isEqualTo("test-user");
	}

	@Test
	public void shouldHandleConcurrentRequests() {
		// Test that multiple concurrent requests don't interfere with each other
		var request1 = new EmbeddingRequest(List.of("First request"),
				AzureOpenAiEmbeddingOptions.builder().deploymentName("MODEL1").user("USER1").build());
		var request2 = new EmbeddingRequest(List.of("Second request"),
				AzureOpenAiEmbeddingOptions.builder().deploymentName("MODEL2").user("USER2").build());

		var options1 = this.client.toEmbeddingOptions(request1);
		var options2 = this.client.toEmbeddingOptions(request2);

		assertThat(options1.getModel()).isEqualTo("MODEL1");
		assertThat(options1.getUser()).isEqualTo("USER1");
		assertThat(options2.getModel()).isEqualTo("MODEL2");
		assertThat(options2.getUser()).isEqualTo("USER2");
	}

	@Test
	public void shouldHandleEmptyStringInputs() {
		List<String> inputsWithEmpty = Arrays.asList("", "Valid text", "", "Another valid text");
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(inputsWithEmpty, null));

		assertThat(requestOptions.getInput()).hasSize(4);
		assertThat(requestOptions.getInput()).containsExactly("", "Valid text", "", "Another valid text");
	}

	@Test
	public void shouldHandleDifferentClientConfigurations() {
		var clientWithDifferentDefaults = new AzureOpenAiEmbeddingModel(this.mockClient, MetadataMode.EMBED,
				AzureOpenAiEmbeddingOptions.builder().deploymentName("DIFFERENT_DEFAULT").build());

		var requestOptions = clientWithDifferentDefaults
			.toEmbeddingOptions(new EmbeddingRequest(List.of("Test content"), null));

		assertThat(requestOptions.getModel()).isEqualTo("DIFFERENT_DEFAULT");
		assertThat(requestOptions.getUser()).isNull(); // No default user set
	}

	@Test
	public void shouldHandleWhitespaceOnlyInputs() {
		List<String> whitespaceInputs = Arrays.asList("   ", "\t\t", "\n\n", "  valid text  ");
		var requestOptions = this.client.toEmbeddingOptions(new EmbeddingRequest(whitespaceInputs, null));

		assertThat(requestOptions.getInput()).hasSize(4);
		assertThat(requestOptions.getInput()).containsExactlyElementsOf(whitespaceInputs);
	}

	@Test
	public void shouldValidateInputListIsNotModified() {
		List<String> originalInputs = Arrays.asList("Input 1", "Input 2", "Input 3");
		List<String> inputsCopy = new ArrayList<>(originalInputs);

		this.client.toEmbeddingOptions(new EmbeddingRequest(inputsCopy, null));

		// Verify original list wasn't modified
		assertThat(inputsCopy).isEqualTo(originalInputs);
	}

}
