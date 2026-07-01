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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Configuration for Anthropic's built-in web search tool. When enabled, Claude can search
 * the web during a conversation and use the results to generate cited responses.
 *
 * <p>
 * Example usage: <pre>{@code
 * var webSearch = AnthropicWebSearchTool.builder()
 *     .allowedDomains(List.of("docs.spring.io", "github.com"))
 *     .maxUses(5)
 *     .build();
 *
 * var options = AnthropicChatOptions.builder()
 *     .webSearchTool(webSearch)
 *     .build();
 * }</pre>
 *
 * @author Soby Chacko
 * @since 1.0.0
 * @see <a href=
 * "https://docs.anthropic.com/en/docs/agents-and-tools/tool-use/web-search">Anthropic Web
 * Search</a>
 */
public class AnthropicWebSearchTool {

	private @Nullable List<String> allowedDomains;

	private @Nullable List<String> blockedDomains;

	private @Nullable Long maxUses;

	private @Nullable UserLocation userLocation;

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable List<String> getAllowedDomains() {
		return this.allowedDomains;
	}

	public void setAllowedDomains(@Nullable List<String> allowedDomains) {
		this.allowedDomains = allowedDomains;
	}

	public @Nullable List<String> getBlockedDomains() {
		return this.blockedDomains;
	}

	public void setBlockedDomains(@Nullable List<String> blockedDomains) {
		this.blockedDomains = blockedDomains;
	}

	public @Nullable Long getMaxUses() {
		return this.maxUses;
	}

	public void setMaxUses(@Nullable Long maxUses) {
		this.maxUses = maxUses;
	}

	public @Nullable UserLocation getUserLocation() {
		return this.userLocation;
	}

	public void setUserLocation(@Nullable UserLocation userLocation) {
		this.userLocation = userLocation;
	}

	/**
	 * Approximate user location for localizing web search results.
	 *
	 * @param city the city name
	 * @param country the ISO 3166-1 alpha-2 country code
	 * @param region the region or state
	 * @param timezone the IANA timezone identifier
	 */
	public record UserLocation(@Nullable String city, @Nullable String country, @Nullable String region,
			@Nullable String timezone) {
	}

	public static class Builder {

		private @Nullable List<String> allowedDomains;

		private @Nullable List<String> blockedDomains;

		private @Nullable Long maxUses;

		private @Nullable UserLocation userLocation;

		public Builder allowedDomains(List<String> allowedDomains) {
			this.allowedDomains = new ArrayList<>(allowedDomains);
			return this;
		}

		public Builder blockedDomains(List<String> blockedDomains) {
			this.blockedDomains = new ArrayList<>(blockedDomains);
			return this;
		}

		public Builder maxUses(long maxUses) {
			this.maxUses = maxUses;
			return this;
		}

		public Builder userLocation(@Nullable String city, @Nullable String country, @Nullable String region,
				@Nullable String timezone) {
			this.userLocation = new UserLocation(city, country, region, timezone);
			return this;
		}

		public AnthropicWebSearchTool build() {
			AnthropicWebSearchTool tool = new AnthropicWebSearchTool();
			tool.allowedDomains = this.allowedDomains;
			tool.blockedDomains = this.blockedDomains;
			tool.maxUses = this.maxUses;
			tool.userLocation = this.userLocation;
			return tool;
		}

	}

}
