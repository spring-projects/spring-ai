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

package org.springframework.ai.anthropic;

import com.anthropic.models.messages.MessageCreateParams;

/**
 * Service tier for controlling capacity routing on Anthropic API requests.
 *
 * @author Soby Chacko
 * @since 1.0.0
 * @see <a href="https://docs.claude.com/en/api/service-tiers">Anthropic Service Tiers</a>
 */
public enum AnthropicServiceTier {

	/**
	 * Use priority capacity if available, otherwise fall back to standard capacity.
	 */
	AUTO,

	/**
	 * Always use standard capacity.
	 */
	STANDARD_ONLY;

	/**
	 * Converts this enum to the corresponding SDK {@link MessageCreateParams.ServiceTier}
	 * value.
	 * @return the SDK service tier
	 */
	public MessageCreateParams.ServiceTier toSdkServiceTier() {
		return switch (this) {
			case AUTO -> MessageCreateParams.ServiceTier.AUTO;
			case STANDARD_ONLY -> MessageCreateParams.ServiceTier.STANDARD_ONLY;
		};
	}

}
