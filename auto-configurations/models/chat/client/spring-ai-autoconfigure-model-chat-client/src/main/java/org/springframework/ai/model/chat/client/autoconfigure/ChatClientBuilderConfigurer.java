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

package org.springframework.ai.model.chat.client.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;

/**
 * Builder for configuring a {@link ChatClient.Builder}.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Josh Long
 * @author Arjen Poutsma
 * @since 1.0.0 M1
 */
public class ChatClientBuilderConfigurer {

	private @Nullable List<ChatClientCustomizer> customizers;

	void setChatClientCustomizers(List<ChatClientCustomizer> customizers) {
		this.customizers = customizers;
	}

	/**
	 * Configure the specified {@link ChatClient.Builder}. The builder can be further
	 * tuned and default settings can be overridden.
	 * @param builder the {@link ChatClient.Builder} instance to configure
	 * @return the configured builder
	 */
	public ChatClient.Builder configure(ChatClient.Builder builder) {
		applyCustomizers(builder);
		return builder;
	}

	private void applyCustomizers(ChatClient.Builder builder) {
		if (this.customizers != null) {
			for (ChatClientCustomizer customizer : this.customizers) {
				customizer.customize(builder);
			}
		}
	}

}
