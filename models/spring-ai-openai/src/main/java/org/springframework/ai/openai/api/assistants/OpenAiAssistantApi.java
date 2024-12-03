/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai.api.assistants;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.openai.api.assistants.tools.Tool;
import org.springframework.ai.openai.api.assistants.tools.ToolResources;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import static org.springframework.ai.openai.api.common.OpenAiApiConstants.DEFAULT_BASE_URL;

/**
 * OpenAI Assistant API.
 *
 * Provides methods to create, list, retrieve, modify, and delete assistants using
 * OpenAI's Assistants API.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/assistants">Assistants API
 * Reference</a>
 * @author Alexandros Pappas
 */
public class OpenAiAssistantApi {

	public static final String ASSISTANT_ID_CANNOT_BE_NULL_OR_EMPTY = "Assistant ID cannot be null or empty.";

	public static final String V_1_ASSISTANTS_ASSISTANT_ID = "/v1/assistants/{assistant_id}";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	public OpenAiAssistantApi(String openAiToken) {
		this(DEFAULT_BASE_URL, openAiToken, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	public OpenAiAssistantApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(headers -> {
			headers.setBearerAuth(openAiToken);
			headers.set("OpenAI-Beta", "assistants=v2");
			headers.setContentType(MediaType.APPLICATION_JSON);
		}).defaultStatusHandler(responseErrorHandler).build();
	}

	public ResponseEntity<AssistantResponse> createAssistant(CreateAssistantRequest request) {
		Assert.notNull(request, "CreateAssistantRequest cannot be null.");
		Assert.hasLength(request.model(), "Model ID cannot be null or empty.");

		return this.restClient.post().uri("/v1/assistants").body(request).retrieve().toEntity(AssistantResponse.class);
	}

	public ResponseEntity<ListAssistantsResponse> listAssistants(Integer limit, String order, String after,
			String before) {
		return this.restClient.get()
			.uri(uriBuilder -> uriBuilder.path("/v1/assistants")
				.queryParam("limit", limit)
				.queryParam("order", order)
				.queryParam("after", after)
				.queryParam("before", before)
				.build())
			.retrieve()
			.toEntity(ListAssistantsResponse.class);
	}

	public ResponseEntity<AssistantResponse> retrieveAssistant(String assistantId) {
		Assert.hasLength(assistantId, ASSISTANT_ID_CANNOT_BE_NULL_OR_EMPTY);

		return this.restClient.get()
			.uri(uriBuilder -> uriBuilder.path(V_1_ASSISTANTS_ASSISTANT_ID).build(assistantId))
			.retrieve()
			.toEntity(AssistantResponse.class);
	}

	public ResponseEntity<AssistantResponse> modifyAssistant(String assistantId, ModifyAssistantRequest request) {
		Assert.hasLength(assistantId, ASSISTANT_ID_CANNOT_BE_NULL_OR_EMPTY);
		Assert.notNull(request, "ModifyAssistantRequest cannot be null.");

		return this.restClient.post()
			.uri(uriBuilder -> uriBuilder.path(V_1_ASSISTANTS_ASSISTANT_ID).build(assistantId))
			.body(request)
			.retrieve()
			.toEntity(AssistantResponse.class);
	}

	public ResponseEntity<DeleteAssistantResponse> deleteAssistant(String assistantId) {
		Assert.hasLength(assistantId, ASSISTANT_ID_CANNOT_BE_NULL_OR_EMPTY);

		return this.restClient.delete()
			.uri(uriBuilder -> uriBuilder.path(V_1_ASSISTANTS_ASSISTANT_ID).build(assistantId))
			.retrieve()
			.toEntity(DeleteAssistantResponse.class);
	}

	// Request and Response DTOs

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CreateAssistantRequest(@JsonProperty("model") String model, @JsonProperty("name") String name,
			@JsonProperty("description") String description, @JsonProperty("instructions") String instructions,
			@JsonProperty("tools") List<Tool> tools, @JsonProperty("tool_resources") ToolResources toolResources,
			@JsonProperty("metadata") Map<String, String> metadata, @JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP, @JsonProperty("response_format") ResponseFormat responseFormat) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String model;

			private String name;

			private String description;

			private String instructions;

			private List<Tool> tools;

			private ToolResources toolResources;

			private Map<String, String> metadata;

			private Double temperature;

			private Double topP;

			private ResponseFormat responseFormat;

			public Builder withModel(String model) {
				this.model = model;
				return this;
			}

			public Builder withName(String name) {
				this.name = name;
				return this;
			}

			public Builder withDescription(String description) {
				this.description = description;
				return this;
			}

			public Builder withInstructions(String instructions) {
				this.instructions = instructions;
				return this;
			}

			public Builder withTools(List<Tool> tools) {
				this.tools = tools;
				return this;
			}

			public Builder withToolResources(ToolResources toolResources) {
				this.toolResources = toolResources;
				return this;
			}

			public Builder withMetadata(Map<String, String> metadata) {
				this.metadata = metadata;
				return this;
			}

			public Builder withTemperature(Double temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withTopP(Double topP) {
				this.topP = topP;
				return this;
			}

			public Builder withResponseFormat(ResponseFormat responseFormat) {
				this.responseFormat = responseFormat;
				return this;
			}

			public CreateAssistantRequest build() {
				Assert.hasText(this.model, "model must not be empty");

				return new CreateAssistantRequest(this.model, this.name, this.description, this.instructions,
						this.tools, this.toolResources, this.metadata, this.temperature, this.topP,
						this.responseFormat);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record JsonSchema(@JsonProperty("description") String description, @JsonProperty("name") String name,
			@JsonProperty("schema") Map<String, Object> schema, @JsonProperty("strict") Boolean strict) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ListAssistantsResponse(@JsonProperty("object") String object,
			@JsonProperty("data") List<AssistantResponse> data) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record AssistantResponse(@JsonProperty("id") String id, @JsonProperty("object") String object,
			@JsonProperty("created_at") Long createdAt, @JsonProperty("name") String name,
			@JsonProperty("description") String description, @JsonProperty("model") String model,
			@JsonProperty("instructions") String instructions, @JsonProperty("tools") List<Tool> tools,
			@JsonProperty("tool_resources") ToolResources toolResources,
			@JsonProperty("metadata") Map<String, String> metadata, @JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP, @JsonProperty("response_format") ResponseFormat responseFormat) {

		@Override
		public String toString() {
			return "AssistantResponse{" + "id='" + id + '\'' + ", object='" + object + '\'' + ", createdAt=" + createdAt
					+ ", name='" + name + '\'' + ", description='" + description + '\'' + ", model='" + model + '\''
					+ ", instructions='" + instructions + '\'' + ", tools=" + tools + ", toolResources=" + toolResources
					+ ", metadata=" + metadata + ", temperature=" + temperature + ", topP=" + topP + ", responseFormat="
					+ responseFormat + '}';
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String id;

			private String object;

			private Long createdAt;

			private String name;

			private String description;

			private String model;

			private String instructions;

			private List<Tool> tools;

			private ToolResources toolResources;

			private Map<String, String> metadata;

			private Double temperature;

			private Double topP;

			private ResponseFormat responseFormat;

			public Builder withId(String id) {
				this.id = id;
				return this;
			}

			public Builder withObject(String object) {
				this.object = object;
				return this;
			}

			public Builder withCreatedAt(Long createdAt) {
				this.createdAt = createdAt;
				return this;
			}

			public Builder withName(String name) {
				this.name = name;
				return this;
			}

			public Builder withDescription(String description) {
				this.description = description;
				return this;
			}

			public Builder withModel(String model) {
				this.model = model;
				return this;
			}

			public Builder withInstructions(String instructions) {
				this.instructions = instructions;
				return this;
			}

			public Builder withTools(List<Tool> tools) {
				this.tools = tools;
				return this;
			}

			public Builder withToolResources(ToolResources toolResources) {
				this.toolResources = toolResources;
				return this;
			}

			public Builder withMetadata(Map<String, String> metadata) {
				this.metadata = metadata;
				return this;
			}

			public Builder withTemperature(Double temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withTopP(Double topP) {
				this.topP = topP;
				return this;
			}

			public Builder withResponseFormat(ResponseFormat responseFormat) {
				this.responseFormat = responseFormat;
				return this;
			}

			public AssistantResponse build() {
				return new AssistantResponse(id, object, createdAt, name, description, model, instructions, tools,
						toolResources, metadata, temperature, topP, responseFormat);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ModifyAssistantRequest(@JsonProperty("model") String model, @JsonProperty("name") String name,
			@JsonProperty("description") String description, @JsonProperty("instructions") String instructions,
			@JsonProperty("tools") List<Tool> tools, @JsonProperty("tool_resources") ToolResources toolResources,
			@JsonProperty("metadata") Map<String, String> metadata, @JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP, @JsonProperty("response_format") ResponseFormat responseFormat) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String model;

			private String name;

			private String description;

			private String instructions;

			private List<Tool> tools;

			private ToolResources toolResources;

			private Map<String, String> metadata;

			private Double temperature;

			private Double topP;

			private ResponseFormat responseFormat;

			public Builder withModel(String model) {
				this.model = model;
				return this;
			}

			public Builder withName(String name) {
				this.name = name;
				return this;
			}

			public Builder withDescription(String description) {
				this.description = description;
				return this;
			}

			public Builder withInstructions(String instructions) {
				this.instructions = instructions;
				return this;
			}

			public Builder withTools(List<Tool> tools) {
				this.tools = tools;
				return this;
			}

			public Builder withToolResources(ToolResources toolResources) {
				this.toolResources = toolResources;
				return this;
			}

			public Builder withMetadata(Map<String, String> metadata) {
				this.metadata = metadata;
				return this;
			}

			public Builder withTemperature(Double temperature) {
				this.temperature = temperature;
				return this;
			}

			public Builder withTopP(Double topP) {
				this.topP = topP;
				return this;
			}

			public Builder withResponseFormat(ResponseFormat responseFormat) {
				this.responseFormat = responseFormat;
				return this;
			}

			public ModifyAssistantRequest build() {
				return new ModifyAssistantRequest(model, name, description, instructions, tools, toolResources,
						metadata, temperature, topP, responseFormat);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DeleteAssistantResponse(@JsonProperty("id") String id, @JsonProperty("object") String object,
			@JsonProperty("deleted") Boolean deleted) {
	}

	public enum ResponseFormatType {

		@JsonProperty("auto")
		AUTO,

		/**
		 * all tools must be of type `function` when `response_format` is of type
		 * `json_schema`.
		 */
		@JsonProperty("json_object")
		JSON_OBJECT,

		@JsonProperty("text")
		TEXT,

		/**
		 * all tools must be of type `function` when `response_format` is of type
		 * `json_schema`.
		 */
		@JsonProperty("json_schema")
		JSON_SCHEMA

	}

}
