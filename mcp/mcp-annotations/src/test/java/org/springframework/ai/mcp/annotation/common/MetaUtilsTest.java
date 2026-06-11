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

package org.springframework.ai.mcp.annotation.common;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpCsp;
import org.springframework.ai.mcp.annotation.Visibility;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class MetaUtilsTest {

	@Test
	void testGetMetaNonNull() {

		Map<String, Object> actual = MetaUtils.getMeta(MetaProviderWithDefaultConstructor.class);

		assertThat(actual).containsExactlyInAnyOrderEntriesOf(new MetaProviderWithDefaultConstructor().getMeta());
	}

	@Test
	void testGetMetaWithPublicConstructor() {

		Map<String, Object> actual = MetaUtils.getMeta(MetaProviderWithAvailableConstructor.class);

		assertThat(actual).containsExactlyInAnyOrderEntriesOf(new MetaProviderWithAvailableConstructor().getMeta());
	}

	@Test
	void testGetMetaWithUnavailableConstructor() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> MetaUtils.getMeta(MetaProviderWithUnavailableConstructor.class))
			.withMessage(
					"org.springframework.ai.mcp.annotation.common.MetaUtilsTest$MetaProviderWithUnavailableConstructor instantiation failed");
	}

	@Test
	void testGetMetaWithConstructorWithWrongSignature() {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> MetaUtils.getMeta(MetaProviderWithConstructorWithWrongSignature.class))
			.withMessage(
					"Required no-arg constructor not found in org.springframework.ai.mcp.annotation.common.MetaUtilsTest$MetaProviderWithConstructorWithWrongSignature");
	}

	@Test
	void testGetMetaNull() {

		Map<String, Object> actual = MetaUtils.getMeta(DefaultMetaProvider.class);

		assertThat(actual).isNull();
	}

	@Test
	void testMetaProviderClassIsNullReturnsNull() {

		Map<String, Object> actual = MetaUtils.getMeta(null);

		assertThat(actual).isNull();
	}

	@Test
	void testBuildUiMetaWithResourceUri() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html", new Visibility[0], null);

		assertThat(uiMeta).isNotNull();
		assertThat(uiMeta).containsKey("ui");
		assertThat(uiMeta).containsKey("ui/resourceUri");
		assertThat(uiMeta.get("ui/resourceUri")).isEqualTo("ui://test/view.html");

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui.get("resourceUri")).isEqualTo("ui://test/view.html");
	}

	@Test
	void testBuildUiMetaWithVisibility() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html", new Visibility[] { Visibility.APP },
				null);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui.get("visibility")).isEqualTo(List.of("app"));
	}

	@Test
	void testBuildUiMetaWithMultipleVisibility() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html",
				new Visibility[] { Visibility.MODEL, Visibility.APP }, null);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui.get("visibility")).isEqualTo(List.of("model", "app"));
	}

	@Test
	void testBuildUiMetaWithCsp() {
		McpCsp csp = mock(McpCsp.class);
		when(csp.connectDomains()).thenReturn(new String[] { "https://api.example.com" });
		when(csp.resourceDomains()).thenReturn(new String[] { "https://cdn.example.com" });
		when(csp.redirectDomains()).thenReturn(new String[] {});

		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html", new Visibility[0], csp);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		@SuppressWarnings("unchecked")
		Map<String, Object> cspMap = (Map<String, Object>) ui.get("csp");
		assertThat(cspMap.get("connectDomains")).isEqualTo(List.of("https://api.example.com"));
		assertThat(cspMap.get("resourceDomains")).isEqualTo(List.of("https://cdn.example.com"));
		assertThat(cspMap).doesNotContainKey("redirectDomains");
	}

	@Test
	void testBuildUiMetaWithEmptyResourceUri() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("", new Visibility[0], null);
		assertThat(uiMeta).isNull();
	}

	@Test
	void testBuildUiMetaWithBlankResourceUri() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("   ", new Visibility[0], null);
		assertThat(uiMeta).isNull();
	}

	@Test
	void testMergeMetaProviderWinsOnConflict() {
		Map<String, Object> providerMeta = Map.of("ui",
				Map.of("resourceUri", "ui://provider/override.html", "custom", "value"), "other", "data");

		Map<String, Object> annotationMeta = Map.of("ui",
				Map.of("resourceUri", "ui://annotation/view.html", "visibility", List.of("app")), "ui/resourceUri",
				"ui://annotation/view.html");

		Map<String, Object> merged = MetaUtils.mergeMeta(providerMeta, annotationMeta);

		assertThat(merged).containsKey("other");
		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) merged.get("ui");
		assertThat(ui.get("resourceUri")).isEqualTo("ui://provider/override.html");
		assertThat(ui.get("custom")).isEqualTo("value");
		assertThat(ui.get("visibility")).isEqualTo(List.of("app"));
	}

	@Test
	void testMergeMetaKeepsFlatAliasConsistentWithNestedUi() {
		Map<String, Object> providerMeta = Map.of("ui", Map.of("resourceUri", "ui://provider/override.html"));
		Map<String, Object> annotationMeta = MetaUtils.buildUiMeta("ui://annotation/view.html", new Visibility[0],
				null);

		Map<String, Object> merged = MetaUtils.mergeMeta(providerMeta, annotationMeta);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) merged.get("ui");
		assertThat(ui.get("resourceUri")).isEqualTo("ui://provider/override.html");
		assertThat(merged.get("ui/resourceUri")).isEqualTo("ui://provider/override.html");
	}

	@Test
	void testMergeMetaBothNull() {
		Map<String, Object> merged = MetaUtils.mergeMeta(null, null);
		assertThat(merged).isNull();
	}

	@Test
	void testMergeMetaOnlyProvider() {
		Map<String, Object> providerMeta = Map.of("key", "value");
		Map<String, Object> merged = MetaUtils.mergeMeta(providerMeta, null);
		assertThat(merged).isEqualTo(providerMeta);
	}

	@Test
	void testMergeMetaOnlyAnnotation() {
		Map<String, Object> annotationMeta = Map.of("ui", Map.of("resourceUri", "ui://test/view.html"));
		Map<String, Object> merged = MetaUtils.mergeMeta(null, annotationMeta);
		assertThat(merged).isEqualTo(annotationMeta);
	}

	static class MetaProviderWithDefaultConstructor implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("a", "1", "b", "2");
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithAvailableConstructor extends MetaProviderWithDefaultConstructor {

		MetaProviderWithAvailableConstructor() {
			// Nothing to do here
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithUnavailableConstructor extends MetaProviderWithDefaultConstructor {

		private MetaProviderWithUnavailableConstructor() {
			// Nothing to do here
		}

	}

	@SuppressWarnings("unused")
	static final class MetaProviderWithConstructorWithWrongSignature extends MetaProviderWithDefaultConstructor {

		private MetaProviderWithConstructorWithWrongSignature(int invalid) {
			// Nothing to do here
		}

	}

}
