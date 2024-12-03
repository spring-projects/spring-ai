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

package org.springframework.ai.openai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.AssistantResponse;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.CreateAssistantRequest;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.ListAssistantsResponse;
import org.springframework.ai.openai.api.assistants.OpenAiAssistantApi.ModifyAssistantRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * OpenAiAssistantModel provides a high-level abstraction for managing OpenAI assistants
 * by utilizing the OpenAiAssistantApi to interact with the OpenAI Assistants API.
 *
 * This model is responsible for creating, modifying, retrieving, listing, and deleting
 * assistants while supporting retry mechanisms and default options.
 *
 * @author Alexandros Pappas
 */
public class OpenAiAssistantManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAiAssistantApi openAiAssistantApi;

	private final RetryTemplate retryTemplate;

	private OpenAiAssistantOptions defaultOptions;

	public OpenAiAssistantManager(OpenAiAssistantApi openAiAssistantApi) {
		this(openAiAssistantApi, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public OpenAiAssistantManager(OpenAiAssistantApi openAiAssistantApi, RetryTemplate retryTemplate) {
		Assert.notNull(openAiAssistantApi, "OpenAiAssistantApi must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.openAiAssistantApi = openAiAssistantApi;
		this.retryTemplate = retryTemplate;
	}

	public OpenAiAssistantOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public OpenAiAssistantManager withDefaultOptions(OpenAiAssistantOptions defaultOptions) {
		this.defaultOptions = defaultOptions;
		return this;
	}

	public AssistantResponse createAssistant(CreateAssistantRequest createRequest) {
		Assert.notNull(createRequest, "CreateAssistantRequest cannot be null.");
		CreateAssistantRequest finalRequest = mergeOptions(createRequest, this.defaultOptions);

		ResponseEntity<AssistantResponse> responseEntity = this.retryTemplate
			.execute(ctx -> this.openAiAssistantApi.createAssistant(finalRequest));

		AssistantResponse responseBody = responseEntity.getBody();
		if (responseBody == null) {
			logger.warn("No assistant created for request: {}", finalRequest);
			return AssistantResponse.builder().build();
		}

		return responseBody;
	}

	public AssistantResponse modifyAssistant(String assistantId, ModifyAssistantRequest modifyRequest) {
		Assert.hasLength(assistantId, "Assistant ID cannot be null or empty.");
		Assert.notNull(modifyRequest, "ModifyAssistantRequest cannot be null.");

		ResponseEntity<AssistantResponse> responseEntity = this.retryTemplate
			.execute(ctx -> this.openAiAssistantApi.modifyAssistant(assistantId, modifyRequest));

		AssistantResponse responseBody = responseEntity.getBody();
		if (responseBody == null) {
			logger.warn("No assistant modification response for assistantId={} and request: {}", assistantId,
					modifyRequest);
			return AssistantResponse.builder().build();
		}

		return responseBody;
	}

	public AssistantResponse retrieveAssistant(String assistantId) {
		Assert.hasLength(assistantId, "Assistant ID cannot be null or empty.");

		ResponseEntity<AssistantResponse> responseEntity = this.retryTemplate
			.execute(ctx -> this.openAiAssistantApi.retrieveAssistant(assistantId));

		AssistantResponse responseBody = responseEntity.getBody();
		if (responseBody == null) {
			logger.warn("No assistant retrieved for assistantId={}", assistantId);
			return AssistantResponse.builder().build();
		}

		return responseBody;
	}

	public ListAssistantsResponse listAssistants(int limit, String order, String after, String before) {
		ResponseEntity<ListAssistantsResponse> responseEntity = this.retryTemplate
			.execute(ctx -> this.openAiAssistantApi.listAssistants(limit, order, after, before));

		ListAssistantsResponse responseBody = responseEntity.getBody();
		if (responseBody == null) {
			logger.warn("No assistants returned for list request with limit={}, order={}, after={}, before={}", limit,
					order, after, before);
			return new ListAssistantsResponse("list", List.of());
		}

		return responseBody;
	}

	public boolean deleteAssistant(String assistantId) {
		Assert.hasLength(assistantId, "Assistant ID cannot be null or empty.");

		ResponseEntity<OpenAiAssistantApi.DeleteAssistantResponse> responseEntity = this.retryTemplate
			.execute(ctx -> this.openAiAssistantApi.deleteAssistant(assistantId));

		OpenAiAssistantApi.DeleteAssistantResponse responseBody = responseEntity.getBody();
		if (responseBody == null || responseBody.deleted() == null) {
			logger.warn("No delete response or null deletion flag for assistantId={}", assistantId);
			return false;
		}

		return Boolean.TRUE.equals(responseBody.deleted());
	}

	/**
	 * Merges the default options with the request-specific options if present.
	 */
	private <T> T mergeOptions(T request, OpenAiAssistantOptions defaultOptions) {
		if (defaultOptions != null) {
			return ModelOptionsUtils.merge(defaultOptions, request, (Class<T>) request.getClass());
		}
		return request;
	}

}
