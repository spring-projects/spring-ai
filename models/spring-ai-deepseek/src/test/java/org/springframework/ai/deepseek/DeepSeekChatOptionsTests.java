/*
 * Copyright 2024-present the original author or authors.
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

package org.springframework.ai.deepseek;

import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

/**
 * Tests for {@link DeepSeekChatOptions}.
 *
 * @author Geng Rong
 */
class DeepSeekChatOptionsTests<B extends DeepSeekChatOptions.Builder<B>>
		extends AbstractChatOptionsTests<DeepSeekChatOptions, B> {

	@Override
	protected Class<DeepSeekChatOptions> getConcreteOptionsClass() {
		return DeepSeekChatOptions.class;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected B readyToBuildBuilder() {
		return (B) DeepSeekChatOptions.builder().model(DeepSeekApi.DEFAULT_CHAT_MODEL);
	}

}
