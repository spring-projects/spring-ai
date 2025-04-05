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

package org.springframework.ai.chat.client.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link ObservationFilter} to include the chat prompt content in the observation.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ChatClientInputContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatClientObservationContext chatClientObservationContext)) {
			return context;
		}

		chatClientSystemText(chatClientObservationContext);
		chatClientSystemParams(chatClientObservationContext);
		chatClientUserText(chatClientObservationContext);
		chatClientUserParams(chatClientObservationContext);

		return chatClientObservationContext;
	}

	protected void chatClientSystemText(ChatClientObservationContext context) {
		if (!StringUtils.hasText(context.getRequest().getSystemText())) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT,
						context.getRequest().getSystemText()));
	}

	protected void chatClientSystemParams(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getSystemParams())) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM,
						TracingHelper.concatenateMaps(context.getRequest().getSystemParams())));
	}

	protected void chatClientUserText(ChatClientObservationContext context) {
		if (!StringUtils.hasText(context.getRequest().getUserText())) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT,
						context.getRequest().getUserText()));
	}

	protected void chatClientUserParams(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getUserParams())) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAMS,
						TracingHelper.concatenateMaps(context.getRequest().getUserParams())));
	}

}
