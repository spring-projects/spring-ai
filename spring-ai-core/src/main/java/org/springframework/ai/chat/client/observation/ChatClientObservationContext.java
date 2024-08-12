/*
* Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.chat.client.observation;

import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;

import io.micrometer.observation.Observation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class ChatClientObservationContext extends Observation.Context {

	private final boolean stream;

	private final String format;

	private final DefaultChatClientRequestSpec request;

	private final AiOperationMetadata operationMetadata = new AiOperationMetadata(AiOperationType.FRAMEWORK.value(),
			AiProvider.SPRING_AI.value());

	public ChatClientObservationContext(DefaultChatClientRequestSpec requestSpec, String format, Boolean isStream) {

		this.request = requestSpec;
		this.format = format;
		this.stream = isStream;
	}

	public DefaultChatClientRequestSpec getRequest() {
		return this.request;
	}

	public AiOperationMetadata getOperationMetadata() {
		return this.operationMetadata;
	}

	public boolean isStream() {
		return this.stream;
	}

	public String getFormat() {
		return this.format;
	}

}