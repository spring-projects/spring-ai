/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropic.api.tool.search;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.anthropic.api.tool.Tool;
import org.springframework.util.Assert;

/**
 * implementation for [WebSearchTool](<a href=
 * "https://docs.anthropic.com/en/docs/agents-and-tools/tool-use/web-search-tool">...</a>)
 *
 * @author Jonghoon Park
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSearchTool extends Tool {

	public static Builder builder() {
		return new Builder();
	}

	private static final String WEB_SEARCH = "web_search";

	private static final String WEB_SEARCH_20250305 = "web_search_20250305";

	@JsonProperty("type")
	private final String type;

	@JsonProperty("max_uses")
	private final Integer maxUses;

	@JsonProperty("allowed_domains")
	private final List<String> allowedDomains;

	@JsonProperty("blocked_domains")
	private final List<String> blockedDomains;

	@JsonProperty("user_location")
	private final UserLocation userLocation;

	public WebSearchTool(String name, String type, Integer maxUses, List<String> allowedDomains,
			List<String> blockedDomains, UserLocation userLocation) {
		super(name, null, null);
		this.type = type;
		this.maxUses = maxUses;
		this.allowedDomains = allowedDomains;
		this.blockedDomains = blockedDomains;
		this.userLocation = userLocation;
	}

	public static class Builder {

		private String name = WEB_SEARCH;

		private String type = WEB_SEARCH_20250305;

		private Integer maxUses = 5;

		private List<String> allowedDomains;

		private List<String> blockedDomains;

		private UserLocation userLocation;

		public Builder name(String name) {
			Assert.notNull(name, "name cannot be null");
			this.name = name;
			return this;
		}

		public Builder type(String type) {
			Assert.notNull(type, "type cannot be null");
			this.type = type;
			return this;
		}

		public Builder maxUses(int maxUses) {
			this.maxUses = maxUses;
			return this;
		}

		public Builder allowedDomains(List<String> allowedDomains) {
			Assert.notNull(allowedDomains, "allowedDomains cannot be null");
			this.allowedDomains = allowedDomains;
			return this;
		}

		public Builder blockedDomains(List<String> blockedDomains) {
			Assert.notNull(blockedDomains, "blockedDomains cannot be null");
			this.blockedDomains = blockedDomains;
			return this;
		}

		public Builder userLocation(UserLocation userLocation) {
			Assert.notNull(userLocation, "userLocation cannot be null");
			this.userLocation = userLocation;
			return this;
		}

		public WebSearchTool build() {
			return new WebSearchTool(this.name, this.type, this.maxUses, this.allowedDomains, this.blockedDomains,
					this.userLocation);
		}

	}

}
