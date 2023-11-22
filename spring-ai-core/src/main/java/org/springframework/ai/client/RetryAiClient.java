/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.client;

import org.springframework.ai.CommonUtils;
import org.springframework.ai.prompt.Prompt;
import org.springframework.retry.support.RetryTemplate;

/**
 * The {@link RetryAiClient} is a {@link AiClient} decorator that automatically re-invoke
 * the failed generate operations according to pre-configured retry policies. This is
 * helpful transient errors such as a momentary network glitch.
 *
 * @author Christian Tzolov
 */
public class RetryAiClient implements AiClient {

	private final RetryTemplate retryTemplate;

	private final AiClient delegate;

	public RetryAiClient(AiClient delegate) {
		this(CommonUtils.DEFAULT_RETRY_TEMPLATE, delegate);
	}

	public RetryAiClient(RetryTemplate retryTemplate, AiClient delegate) {
		this.retryTemplate = retryTemplate;
		this.delegate = delegate;
	}

	@Override
	public AiResponse generate(Prompt prompt) {
		return this.retryTemplate.execute(ctx -> {
			return this.delegate.generate(prompt);
		});
	}

}
