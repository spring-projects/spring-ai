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

package org.springframework.ai.model.observation;

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.util.Assert;

/**
 * Context used when sending a request to a machine learning model and waiting for a
 * response from the model provider.
 *
 * @param <REQ> type of the request object
 * @param <RES> type of the response object
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ModelObservationContext<REQ, RES> extends Observation.Context {

	private final REQ request;

	private final AiOperationMetadata operationMetadata;

	private @Nullable RES response;

	public ModelObservationContext(REQ request, AiOperationMetadata operationMetadata) {
		Assert.notNull(request, "request cannot be null");
		Assert.notNull(operationMetadata, "operationMetadata cannot be null");
		this.request = request;
		this.operationMetadata = operationMetadata;
	}

	public REQ getRequest() {
		return this.request;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public @Nullable RES getResponse() {
		return this.response;
	}

	public void setResponse(RES response) {
		Assert.notNull(response, "response cannot be null");
		this.response = response;
	}

}
