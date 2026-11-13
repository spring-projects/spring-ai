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

package org.springframework.ai.tool.toolsearch.index.openai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.ChatModel;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.Response.ToolChoice;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseToolSearchOutputItem;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiToolIndex}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class OpenAiToolIndexTests {

	private static final String SESSION = "session-1";

	private static final String MODEL = ChatModel.GPT_5_4_MINI.asString();

	@Mock
	private OpenAIClient openAiClient;

	@Mock
	private ResponseService responseService;

	private ToolReference ref(String name, String description) {
		return ToolReference.builder()
				.toolName(name)
				.summary(description)
				.build();
	}

	private OpenAiToolIndex newToolIndex() {
		return OpenAiToolIndex.builder()
				.openAiClient(this.openAiClient)
				.model(MODEL)
				.build();
	}

	private ToolSearchResponse search(OpenAiToolIndex toolIndex, String query) {
		return toolIndex.search(new ToolSearchRequest(SESSION, query, null, null));
	}

	private Response responseWithTools(FunctionTool... tools) {
		ResponseToolSearchOutputItem toolSearchOutputItem = ResponseToolSearchOutputItem.builder()
			.id("tool-search-output-1")
			.callId("tool-search-call-1")
			.execution(ResponseToolSearchOutputItem.Execution.SERVER)
			.status(ResponseToolSearchOutputItem.Status.COMPLETED)
			.tools(Stream.of(tools)
					.map(Tool::ofFunction)
					.toList())
			.build();

		return Response.builder()
			.id("response-1")
			.createdAt(1_000.0)
			.error(Optional.empty())
			.incompleteDetails(Optional.empty())
			.instructions(Optional.empty())
			.metadata(Optional.empty())
			.model(MODEL)
			.output(List.of(ResponseOutputItem.ofToolSearchOutput(toolSearchOutputItem)))
			.parallelToolCalls(false)
			.temperature(1.0)
			.toolChoice(ToolChoice.ofOptions(ToolChoiceOptions.AUTO))
			.tools(List.of())
			.topP(1.0)
			.completedAt(1_001.25)
			.build();
	}

	private FunctionTool functionTool(String name, String description) {
		return FunctionTool.builder()
			.name(name)
			.description(description)
			.parameters(Optional.empty())
			.strict(false)
			.build();
	}

	@Test
	void builderRequiresOpenAiClient() {
		assertThatIllegalArgumentException().isThrownBy(() -> OpenAiToolIndex.builder().model(MODEL).build())
			.withMessage("openAiClient must not be null");
	}

	@Test
	void builderRequiresModel() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> OpenAiToolIndex.builder().openAiClient(this.openAiClient).build())
			.withMessage("model must not be null");
	}

	@Test
	void builderRejectsModelsBeforeToolSearchSupport() {
		assertThatThrownBy(() -> OpenAiToolIndex.builder()
			.openAiClient(this.openAiClient)
			.model(ChatModel.GPT_4O.asString())
			.build())
			.isInstanceOf(OpenAIException.class)
			.hasMessage("Only gpt-5.4 and later models support tool search.");
	}

	@Test
	void searchReturnsEmptyForSessionWithoutTools() {
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			ToolSearchResponse response = search(toolIndex, "weather");

			assertThat(response.toolReferences()).isEmpty();
			verify(this.responseService, never()).create(any(ResponseCreateParams.class));
		}
	}

	@Test
	void searchSendsQueryAndIndexedToolsToOpenAi() {
		when(this.openAiClient.responses()).thenReturn(this.responseService);
		when(this.responseService.create(any(ResponseCreateParams.class)))
			.thenReturn(responseWithTools(functionTool("weatherTool", "Returns current weather conditions")));
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			toolIndex.indexTool(SESSION, ref("weatherTool", "Returns current weather conditions"));

			ToolSearchResponse response = search(toolIndex, "weather");

			assertThat(response.toolReferences()).hasSize(1);
			assertThat(response.toolReferences().get(0).toolName()).isEqualTo("weatherTool");
			assertThat(response.toolReferences().get(0).summary()).isEqualTo("Returns current weather conditions");

			ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
			verify(this.responseService).create(captor.capture());

			ResponseCreateParams params = captor.getValue();
			assertThat(params.input()).hasValueSatisfying(input -> assertThat(input.asText()).isEqualTo("weather"));
			assertThat(params.model()).hasValueSatisfying(model -> assertThat(model.asString()).isEqualTo(MODEL));
			assertThat(params.parallelToolCalls()).contains(false);
			assertThat(params.tools()).hasValueSatisfying(tools -> {
				assertThat(tools).hasSize(2);
				assertThat(tools.get(0).isSearch()).isTrue();
				assertThat(tools.get(1).asFunction().name()).isEqualTo("weatherTool");
				assertThat(tools.get(1).asFunction().description()).contains("Returns current weather conditions");
				assertThat(tools.get(1).asFunction().parameters()).isEmpty();
				assertThat(tools.get(1).asFunction().strict()).contains(false);
			});
		}
	}

	@Test
	void indexToolsBatchAddsAllToolsToOpenAiRequest() {
		when(this.openAiClient.responses()).thenReturn(this.responseService);
		when(this.responseService.create(any(ResponseCreateParams.class))).thenReturn(responseWithTools());
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			toolIndex.indexTools(SESSION,
					List.of(ref("tool1", "First tool"), ref("tool2", "Second tool"), ref("tool3", "Third tool")));

			search(toolIndex, "tool");

			ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
			verify(this.responseService).create(captor.capture());

			assertThat(captor.getValue().tools()).hasValueSatisfying(tools -> {
				assertThat(tools).hasSize(4);
				assertThat(tools.stream().filter(Tool::isFunction).map(tool -> tool.asFunction().name()))
					.containsExactly("tool1", "tool2", "tool3");
			});
		}
	}

	@Test
	void clearIndexRemovesSessionTools() {
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			toolIndex.indexTools(SESSION, List.of(ref("tool1", "desc1"), ref("tool2", "desc2")));

			toolIndex.clearIndex(SESSION);

			ToolSearchResponse response = search(toolIndex, "tool");
			assertThat(response.toolReferences()).isEmpty();
			verify(this.responseService, never()).create(any(ResponseCreateParams.class));
		}
	}

	@Test
	void sessionIsolationPreventsLeakage() {
		when(this.openAiClient.responses()).thenReturn(this.responseService);
		when(this.responseService.create(any(ResponseCreateParams.class))).thenReturn(responseWithTools());
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			toolIndex.indexTool(SESSION, ref("weatherTool", "Weather data"));
			toolIndex.indexTool("session-2", ref("calculatorTool", "Math operations"));

			toolIndex.search(new ToolSearchRequest("session-2", "calculator", null, null));

			ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
			verify(this.responseService).create(captor.capture());
			assertThat(captor.getValue().tools()).hasValueSatisfying(tools -> {
				assertThat(tools).hasSize(2);
				assertThat(tools.get(1).asFunction().name()).isEqualTo("calculatorTool");
			});
		}
	}

	@Test
	void searchMetadataContainsSearchTypeQueryAndElapsedTime() {
		when(this.openAiClient.responses()).thenReturn(this.responseService);
		when(this.responseService.create(any(ResponseCreateParams.class)))
			.thenReturn(responseWithTools(functionTool("weatherTool", "Weather data")));
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			toolIndex.indexTool(SESSION, ref("weatherTool", "Weather data"));

			ToolSearchResponse response = search(toolIndex, "weather");
			ToolSearchResponse.SearchMetadata searchMetadata = Objects.requireNonNull(response.searchMetadata());

			assertThat(searchMetadata.searchType()).isEqualTo("OpenAiToolIndex");
			assertThat(searchMetadata.query()).isEqualTo("weather");
			assertThat(searchMetadata.searchTimeMs()).isEqualTo(1_250L);
		}
	}

	@Test
	void closeClosesOpenAiClient() {
		try (OpenAiToolIndex toolIndex = newToolIndex()) {
			assertThat(toolIndex).isNotNull();
		}

		verify(this.openAiClient).close();
	}

}
