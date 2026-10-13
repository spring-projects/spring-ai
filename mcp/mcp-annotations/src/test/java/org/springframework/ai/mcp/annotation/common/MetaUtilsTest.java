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

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.EmptyMcpCsp;
import org.springframework.ai.mcp.annotation.McpCsp;
import org.springframework.ai.mcp.annotation.McpPermission;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.PrefersBorder;
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
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html", new Visibility[0]);

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
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html", new Visibility[] { Visibility.APP });

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui.get("visibility")).isEqualTo(List.of("app"));
	}

	@Test
	void testBuildUiMetaWithMultipleVisibility() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html",
				new Visibility[] { Visibility.MODEL, Visibility.APP });

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui.get("visibility")).isEqualTo(List.of("model", "app"));
	}

	@Test
	void testBuildUiMetaNeverEmitsCsp() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("ui://test/view.html",
				new Visibility[] { Visibility.MODEL, Visibility.APP });

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) uiMeta.get("ui");
		assertThat(ui).containsOnlyKeys("resourceUri", "visibility");
		assertThat(uiMeta).containsOnlyKeys("ui", "ui/resourceUri");
	}

	@Test
	void testBuildResourceUiMetaCspWithConnectAndResourceDomains() {
		McpCsp csp = mock(McpCsp.class);
		when(csp.connectDomains()).thenReturn(new String[] { "https://api.example.com" });
		when(csp.resourceDomains()).thenReturn(new String[] { "https://cdn.example.com" });
		when(csp.frameDomains()).thenReturn(new String[] {});
		when(csp.baseUriDomains()).thenReturn(new String[] {});

		Map<String, Object> cspMap = buildCspMap(csp);

		assertThat(cspMap.get("connectDomains")).isEqualTo(List.of("https://api.example.com"));
		assertThat(cspMap.get("resourceDomains")).isEqualTo(List.of("https://cdn.example.com"));
		assertThat(cspMap).doesNotContainKeys("frameDomains", "baseUriDomains", "redirectDomains");
	}

	@Test
	void testBuildResourceUiMetaCspWithAllFourCspFieldsInSpecOrder() {
		McpCsp csp = cspOf(AllCspFields.class);

		Map<String, Object> cspMap = buildCspMap(csp);

		assertThat(cspMap).containsOnlyKeys("connectDomains", "resourceDomains", "frameDomains", "baseUriDomains");
		assertThat(cspMap.keySet()).containsExactly("connectDomains", "resourceDomains", "frameDomains",
				"baseUriDomains");
		assertThat(cspMap.get("connectDomains")).isEqualTo(List.of("https://api.example.com"));
		assertThat(cspMap.get("resourceDomains"))
			.isEqualTo(List.of("https://cdn.example.com", "https://*.example.org"));
		assertThat(cspMap.get("frameDomains")).isEqualTo(List.of("https://embed.example.com"));
		assertThat(cspMap.get("baseUriDomains")).isEqualTo(List.of("https://static.example.com"));
	}

	@Test
	void testBuildResourceUiMetaCspWithOnlyFrameAndBaseUriDomains() {
		McpCsp csp = cspOf(FrameAndBaseUriOnly.class);

		Map<String, Object> cspMap = buildCspMap(csp);

		assertThat(cspMap).containsOnlyKeys("frameDomains", "baseUriDomains");
		assertThat(cspMap.get("frameDomains")).isEqualTo(List.of("https://embed.example.com"));
		assertThat(cspMap.get("baseUriDomains")).isEqualTo(List.of("https://static.example.com"));
	}

	@Test
	void testBuildResourceUiMetaCspOmitsCspWhenAllFieldsEmpty() {
		assertThat(MetaUtils.buildResourceUiMeta(cspOf(EmptyCsp.class), null, null, null)).isNull();
	}

	@Test
	void testBuildResourceUiMetaCspWithNullCsp() {
		assertThat(MetaUtils.buildResourceUiMeta(null, null, null, null)).isNull();
	}

	@Test
	void testBuildResourceUiMetaAllFieldsInSpecOrder() {
		Map<String, Object> meta = MetaUtils.buildResourceUiMeta(cspOf(AllCspFields.class),
				new McpPermission[] { McpPermission.CLIPBOARD_WRITE, McpPermission.CAMERA },
				"a904794854a047f6.claudemcpcontent.com", PrefersBorder.TRUE);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
		assertThat(ui.keySet()).containsExactly("csp", "permissions", "domain", "prefersBorder");
		@SuppressWarnings("unchecked")
		Map<String, Object> permissions = (Map<String, Object>) ui.get("permissions");
		assertThat(permissions.keySet()).containsExactly("camera", "clipboardWrite");
		assertThat(permissions.values()).allSatisfy(v -> assertThat(v).isEqualTo(Map.of()));
		assertThat(ui.get("domain")).isEqualTo("a904794854a047f6.claudemcpcontent.com");
		assertThat(ui.get("prefersBorder")).isEqualTo(true);
	}

	@Test
	void testBuildResourceUiMetaExplicitFalsePrefersBorderIsEmitted() {
		Map<String, Object> meta = MetaUtils.buildResourceUiMeta(EmptyMcpCsp.INSTANCE, new McpPermission[0], "",
				PrefersBorder.FALSE);
		assertThat(meta.get("ui")).isEqualTo(Map.of("prefersBorder", false));
	}

	@Test
	void testBuildResourceUiMetaNothingDeclaredReturnsNull() {
		assertThat(MetaUtils.buildResourceUiMeta(EmptyMcpCsp.INSTANCE, new McpPermission[0], "", PrefersBorder.UNSET))
			.isNull();
		assertThat(MetaUtils.buildResourceUiMeta(null, null, null, null)).isNull();
	}

	@Test
	void testBuildResourceUiMetaBlankDomainOmitted() {
		assertThat(
				MetaUtils.buildResourceUiMeta(EmptyMcpCsp.INSTANCE, new McpPermission[0], "   ", PrefersBorder.UNSET))
			.isNull();
	}

	@Test
	void testBuildResourceUiMetaDuplicatePermissionsCollapsed() {
		Map<String, Object> meta = MetaUtils.buildResourceUiMeta(null,
				new McpPermission[] { McpPermission.CAMERA, McpPermission.CAMERA }, "", PrefersBorder.UNSET);
		assertThat(((Map<?, ?>) meta.get("ui")).get("permissions")).isEqualTo(Map.of("camera", Map.of()));
	}

	@Test
	void testBuildResourceUiMetaSerializesPermissionsAsEmptyObjects() throws IOException {
		Map<String, Object> meta = MetaUtils.buildResourceUiMeta(null,
				new McpPermission[] { McpPermission.CAMERA, McpPermission.CLIPBOARD_WRITE }, "", PrefersBorder.FALSE);

		String json = McpJsonDefaults.getMapper().writeValueAsString(meta).replaceAll("\\s", "");

		assertThat(json).contains("\"permissions\":{\"camera\":{},\"clipboardWrite\":{}}");
		assertThat(json).contains("\"prefersBorder\":false");
	}

	@Test
	void testGetResourceMetaWithoutCspOrProviderIsNull() throws NoSuchMethodException {
		assertThat(MetaUtils.getResourceMeta(resourceAnnotation("plain"))).isNull();
	}

	@Test
	void testGetResourceMetaWithCspOnly() throws NoSuchMethodException {
		Map<String, Object> meta = MetaUtils.getResourceMeta(resourceAnnotation("cspOnly"));

		assertThat(meta).containsOnlyKeys("ui");
		assertThat(cspOf(meta)).containsOnlyKeys("connectDomains", "frameDomains");
	}

	@Test
	void testGetResourceMetaMergesCspWithProviderMeta() throws NoSuchMethodException {
		Map<String, Object> meta = MetaUtils.getResourceMeta(resourceAnnotation("cspAndProvider"));

		assertThat(meta).containsKey("other");
		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
		assertThat(ui).containsKeys("csp", "prefersBorder");
		assertThat(cspOf(meta)).containsOnlyKeys("connectDomains");
	}

	@Test
	void testGetResourceMetaProviderCspWinsOnConflict() throws NoSuchMethodException {
		Map<String, Object> meta = MetaUtils.getResourceMeta(resourceAnnotation("cspConflict"));

		// the provider's ui.csp replaces the annotation's csp as a whole
		assertThat(cspOf(meta)).containsOnlyKeys("resourceDomains");
		assertThat(cspOf(meta).get("resourceDomains")).isEqualTo(List.of("https://provider.example.com"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGetResourceMetaEmitsUiResourceFields() throws NoSuchMethodException {
		Map<String, Object> ui = (Map<String, Object>) MetaUtils.getResourceMeta(resourceAnnotation("uiFields"))
			.get("ui");
		assertThat(ui).isEqualTo(Map.of("permissions", Map.of("microphone", Map.of()), "domain",
				"www-example-com.oaiusercontent.com", "prefersBorder", false));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGetResourceMetaProviderUiKeyWinsPerKey() throws NoSuchMethodException {
		Map<String, Object> ui = (Map<String, Object>) MetaUtils
			.getResourceMeta(resourceAnnotation("uiFieldsWithProvider"))
			.get("ui");
		assertThat(ui).isEqualTo(Map.of("permissions", Map.of("microphone", Map.of()), "domain",
				"www-example-com.oaiusercontent.com", "prefersBorder", true));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGetResourceMetaProviderPermissionsReplaceAnnotationPermissions() throws NoSuchMethodException {
		Map<String, Object> ui = (Map<String, Object>) MetaUtils
			.getResourceMeta(resourceAnnotation("uiFieldsWithProviderPermissions"))
			.get("ui");
		// the provider's ui.permissions replaces the annotation's permissions as a whole
		assertThat(ui).isEqualTo(Map.of("permissions", Map.of("camera", Map.of()), "domain",
				"www-example-com.oaiusercontent.com", "prefersBorder", false));
	}

	@Test
	void testMcpPermissionWireValues() {
		assertThat(McpPermission.values()).extracting(McpPermission::getWireValue)
			.containsExactly("camera", "microphone", "geolocation", "clipboardWrite");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cspOf(Map<String, Object> meta) {
		return (Map<String, Object>) ((Map<String, Object>) meta.get("ui")).get("csp");
	}

	private static McpResource resourceAnnotation(String methodName) throws NoSuchMethodException {
		return ResourceHolder.class.getDeclaredMethod(methodName).getAnnotation(McpResource.class);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> buildCspMap(McpCsp csp) {
		Map<String, Object> meta = MetaUtils.buildResourceUiMeta(csp, null, null, null);
		assertThat(meta).containsOnlyKeys("ui");
		Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
		assertThat(ui).containsOnlyKeys("csp");
		return (Map<String, Object>) ui.get("csp");
	}

	private static McpCsp cspOf(Class<?> holder) {
		return holder.getAnnotation(CspHolder.class).value();
	}

	@Test
	void testBuildUiMetaWithEmptyResourceUri() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("", new Visibility[0]);
		assertThat(uiMeta).isNull();
	}

	@Test
	void testBuildUiMetaWithBlankResourceUri() {
		Map<String, Object> uiMeta = MetaUtils.buildUiMeta("   ", new Visibility[0]);
		assertThat(uiMeta).isNull();
	}

	@Test
	void testBuildUiMetaWithVisibilityOnlyEmitsVisibilityWithoutAlias() {
		Map<String, Object> meta = MetaUtils.buildUiMeta("", new Visibility[] { Visibility.APP });
		assertThat(meta).containsOnlyKeys("ui");
		assertThat(meta.get("ui")).isEqualTo(Map.of("visibility", List.of("app")));
	}

	@Test
	void testBuildUiMetaWithNullUriAndEmptyVisibilityReturnsNull() {
		assertThat(MetaUtils.buildUiMeta(null, new Visibility[0])).isNull();
	}

	@Test
	void testBuildUiMetaDeduplicatesVisibility() {
		Map<String, Object> meta = MetaUtils.buildUiMeta("ui://s/v",
				new Visibility[] { Visibility.MODEL, Visibility.MODEL, Visibility.APP });
		assertThat(((Map<?, ?>) meta.get("ui")).get("visibility")).isEqualTo(List.of("model", "app"));
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
		Map<String, Object> annotationMeta = MetaUtils.buildUiMeta("ui://annotation/view.html", new Visibility[0]);

		Map<String, Object> merged = MetaUtils.mergeMeta(providerMeta, annotationMeta);

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) merged.get("ui");
		assertThat(ui.get("resourceUri")).isEqualTo("ui://provider/override.html");
		assertThat(merged.get("ui/resourceUri")).isEqualTo("ui://provider/override.html");
	}

	@Test
	void testMergeMetaVisibilityOnlyToolMetaWithProvider() {
		Map<String, Object> providerMeta = Map.of("ui", Map.of("custom", "value"), "other", "data");
		Map<String, Object> annotationMeta = MetaUtils.buildUiMeta("", new Visibility[] { Visibility.APP });

		Map<String, Object> merged = MetaUtils.mergeMeta(providerMeta, annotationMeta);

		assertThat(merged)
			.isEqualTo(Map.of("ui", Map.of("visibility", List.of("app"), "custom", "value"), "other", "data"));
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

	@Retention(RetentionPolicy.RUNTIME)
	@interface CspHolder {

		McpCsp value();

	}

	@CspHolder(@McpCsp(connectDomains = "https://api.example.com",
			resourceDomains = { "https://cdn.example.com", "https://*.example.org" },
			frameDomains = "https://embed.example.com", baseUriDomains = "https://static.example.com"))
	static final class AllCspFields {

	}

	@CspHolder(@McpCsp(frameDomains = "https://embed.example.com", baseUriDomains = "https://static.example.com"))
	static final class FrameAndBaseUriOnly {

	}

	@CspHolder(@McpCsp)
	static final class EmptyCsp {

	}

	static final class ResourceHolder {

		@McpResource(uri = "ui://test/plain.html", mimeType = "text/html;profile=mcp-app")
		String plain() {
			return "";
		}

		@McpResource(uri = "ui://test/csp.html", mimeType = "text/html;profile=mcp-app",
				csp = @McpCsp(connectDomains = "https://api.example.com", frameDomains = "https://embed.example.com"))
		String cspOnly() {
			return "";
		}

		@McpResource(uri = "ui://test/merge.html", mimeType = "text/html;profile=mcp-app",
				csp = @McpCsp(connectDomains = "https://api.example.com"), metaProvider = UiPrefsMetaProvider.class)
		String cspAndProvider() {
			return "";
		}

		@McpResource(uri = "ui://test/conflict.html", mimeType = "text/html;profile=mcp-app",
				csp = @McpCsp(connectDomains = "https://api.example.com"), metaProvider = UiCspMetaProvider.class)
		String cspConflict() {
			return "";
		}

		@McpResource(uri = "ui://s/v", mimeType = "text/html;profile=mcp-app", permissions = McpPermission.MICROPHONE,
				domain = "www-example-com.oaiusercontent.com", prefersBorder = PrefersBorder.FALSE)
		String uiFields() {
			return "";
		}

		@McpResource(uri = "ui://s/v", mimeType = "text/html;profile=mcp-app", permissions = McpPermission.MICROPHONE,
				domain = "www-example-com.oaiusercontent.com", prefersBorder = PrefersBorder.FALSE,
				metaProvider = BorderTrueMetaProvider.class)
		String uiFieldsWithProvider() {
			return "";
		}

		@McpResource(uri = "ui://s/v", mimeType = "text/html;profile=mcp-app", permissions = McpPermission.MICROPHONE,
				domain = "www-example-com.oaiusercontent.com", prefersBorder = PrefersBorder.FALSE,
				metaProvider = CameraPermissionsMetaProvider.class)
		String uiFieldsWithProviderPermissions() {
			return "";
		}

	}

	static final class UiPrefsMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("prefersBorder", true), "other", "data");
		}

	}

	static final class UiCspMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("csp", Map.of("resourceDomains", List.of("https://provider.example.com"))));
		}

	}

	static final class BorderTrueMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("prefersBorder", true));
		}

	}

	static final class CameraPermissionsMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("permissions", Map.of("camera", Map.of())));
		}

	}

}
