/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Language;
import com.google.genai.types.Outcome;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for surfacing the Gemini code execution tool parts (the generated code and
 * its execution result) on the response message metadata.
 *
 * @author Subhash Polisetti
 */
public class GoogleGenAiChatModelCodeExecutionTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model("gemini-2.5-flash").build();
		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, options, retryTemplate);
	}

	@Test
	void codeExecutionPartsAreSurfacedInMetadata() {
		Part codePart = Part.builder()
			.executableCode(
					ExecutableCode.builder().code("print(sum(range(10)))").language(Language.Known.PYTHON).build())
			.build();
		Part resultPart = Part.builder()
			.codeExecutionResult(CodeExecutionResult.builder().outcome(Outcome.Known.OUTCOME_OK).output("45").build())
			.build();
		Part textPart = Part.builder().text("The sum is 45.").build();

		Content content = Content.builder().parts(List.of(codePart, resultPart, textPart)).build();
		Candidate candidate = Candidate.builder().content(content).index(0).build();
		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.5-flash")
			.build();
		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("Sum the range"))));

		List<Map<String, Object>> allMetadata = response.getResults()
			.stream()
			.map(generation -> generation.getOutput().getMetadata())
			.toList();

		assertThat(allMetadata).anySatisfy(metadata -> {
			assertThat(metadata).containsEntry(GoogleGenAiChatModel.METADATA_EXECUTABLE_CODE, "print(sum(range(10)))");
			assertThat(metadata).containsEntry(GoogleGenAiChatModel.METADATA_EXECUTABLE_CODE_LANGUAGE, "PYTHON");
		});
		assertThat(allMetadata).anySatisfy(metadata -> {
			assertThat(metadata).containsEntry(GoogleGenAiChatModel.METADATA_CODE_EXECUTION_RESULT, "45");
			assertThat(metadata).containsEntry(GoogleGenAiChatModel.METADATA_CODE_EXECUTION_OUTCOME, "OUTCOME_OK");
		});
		assertThat(response.getResults())
			.anySatisfy(generation -> assertThat(generation.getOutput().getText()).isEqualTo("The sum is 45."));
	}

	@Test
	void responseWithoutCodeExecutionHasNoCodeExecutionMetadata() {
		Content content = Content.builder().parts(Part.builder().text("Plain answer.").build()).build();
		Candidate candidate = Candidate.builder().content(content).index(0).build();
		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.modelVersion("gemini-2.5-flash")
			.build();
		this.chatModel.setMockGenerateContentResponse(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt(List.of(new UserMessage("Hello"))));

		Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
		assertThat(metadata).doesNotContainKey(GoogleGenAiChatModel.METADATA_EXECUTABLE_CODE)
			.doesNotContainKey(GoogleGenAiChatModel.METADATA_CODE_EXECUTION_RESULT);
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Plain answer.");
	}

}
