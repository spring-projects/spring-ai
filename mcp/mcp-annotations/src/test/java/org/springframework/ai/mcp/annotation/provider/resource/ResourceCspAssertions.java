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

package org.springframework.ai.mcp.annotation.provider.resource;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared assertions and fixtures for the SEP-1865 {@code _meta.ui} resource tests.
 *
 * @author Alexandros Pappas
 */
final class ResourceCspAssertions {

	private ResourceCspAssertions() {
	}

	/**
	 * Asserts that the {@code _meta} carries exactly the four SEP-1865 CSP fields, in
	 * specification order, with the values declared by the test fixtures.
	 */
	@SuppressWarnings("unchecked")
	static void assertFullCsp(Map<String, Object> meta) {
		assertThat(meta).isNotNull().containsKey("ui");
		Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
		assertThat(ui).containsKey("csp");
		Map<String, Object> csp = (Map<String, Object>) ui.get("csp");
		assertThat(csp.keySet()).containsExactly("connectDomains", "resourceDomains", "frameDomains", "baseUriDomains");
		assertThat(csp.get("connectDomains")).isEqualTo(List.of("https://api.example.com"));
		assertThat(csp.get("resourceDomains")).isEqualTo(List.of("https://cdn.example.com", "https://*.example.org"));
		assertThat(csp.get("frameDomains")).isEqualTo(List.of("https://embed.example.com"));
		assertThat(csp.get("baseUriDomains")).isEqualTo(List.of("https://static.example.com"));
	}

	/**
	 * Asserts that the {@code _meta} keeps the CSP and the extra {@code ui} entry
	 * contributed by {@link UiPrefsMetaProvider}.
	 */
	@SuppressWarnings("unchecked")
	static void assertMergedCsp(Map<String, Object> meta) {
		assertThat(meta).isNotNull().containsKeys("ui", "other");
		Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
		assertThat(ui).containsKeys("csp", "prefersBorder");
		assertThat(((Map<String, Object>) ui.get("csp")).get("connectDomains"))
			.isEqualTo(List.of("https://api.example.com"));
	}

	/**
	 * Asserts that the {@code _meta} carries exactly the SEP-1865 {@code permissions},
	 * {@code domain} and {@code prefersBorder} declared by the test fixtures under
	 * {@code ui}.
	 */
	static void assertUiResourceFields(Map<String, Object> meta) {
		assertThat(meta).isNotNull().containsOnlyKeys("ui");
		assertThat(meta.get("ui")).isEqualTo(Map.of("permissions", Map.of("microphone", Map.of()), "domain",
				"www-example-com.oaiusercontent.com", "prefersBorder", false));
	}

	/** Contributes a {@code ui.prefersBorder} entry and an unrelated top-level key. */
	public static final class UiPrefsMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("prefersBorder", true), "other", "data");
		}

	}

}
