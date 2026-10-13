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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpCsp;
import org.springframework.ai.mcp.annotation.McpPermission;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.PrefersBorder;
import org.springframework.ai.mcp.annotation.Visibility;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

/**
 * Utility methods for working with {@link MetaProvider} metadata.
 *
 * <p>
 * This class provides a single entry point {@link #getMeta(Class)} that instantiates the
 * given provider type via a no-argument constructor and returns its metadata as an
 * unmodifiable {@link Map}.
 * </p>
 *
 * <p>
 * Instantiation failures and missing no-arg constructors are reported as
 * {@link IllegalArgumentException IllegalArgumentExceptions}. This class is stateless and
 * not intended to be instantiated.
 * </p>
 *
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 * @author Alexandros Pappas
 */
public final class MetaUtils {

	/** Not intended to be instantiated. */
	private MetaUtils() {
	}

	/**
	 * Instantiate the supplied {@link MetaProvider} type using a no-argument constructor
	 * and return the metadata it supplies.
	 * <p>
	 * The returned map is wrapped in {@link Collections#unmodifiableMap(Map)} to prevent
	 * external modification. If the provider returns {@code null}, this method also
	 * returns {@code null}.
	 * @param metaProviderClass the {@code MetaProvider} implementation class to
	 * instantiate; must provide a no-arg constructor
	 * @return an unmodifiable metadata map, or {@code null} if the provider returns
	 * {@code null}
	 * @throws IllegalArgumentException if a no-arg constructor is missing or the instance
	 * cannot be created
	 */
	public static Map<String, Object> getMeta(Class<? extends MetaProvider> metaProviderClass) {

		if (metaProviderClass == null) {
			return null;
		}

		String className = metaProviderClass.getName();
		MetaProvider metaProvider;
		try {
			// Prefer a public no-arg constructor; fall back to a declared no-arg if
			// accessible
			Constructor<? extends MetaProvider> constructor = getConstructor(metaProviderClass);
			metaProvider = constructor.newInstance();
		}
		catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("Required no-arg constructor not found in " + className, e);
		}
		catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException(className + " instantiation failed", e);
		}

		Map<String, Object> meta = metaProvider.getMeta();
		return meta == null ? null : Collections.unmodifiableMap(meta);
	}

	/**
	 * Locate a no-argument constructor on the given class: prefer public, otherwise fall
	 * back to a declared no-arg constructor.
	 * @param metaProviderClass the class to inspect
	 * @return the resolved no-arg constructor
	 * @throws NoSuchMethodException if the class does not declare any no-arg constructor
	 */
	private static Constructor<? extends MetaProvider> getConstructor(Class<? extends MetaProvider> metaProviderClass)
			throws NoSuchMethodException {
		try {
			return metaProviderClass.getDeclaredConstructor();
		}
		catch (NoSuchMethodException ex) {
			return metaProviderClass.getConstructor();
		}
	}

	/**
	 * Build the {@code _meta} map for an MCP App tool from SEP-1865 annotation fields.
	 * <p>
	 * Produces the wire format: <pre>
	 * {
	 *   "ui": { "resourceUri": "ui://...", "visibility": ["model"] },
	 *   "ui/resourceUri": "ui://..."
	 * }
	 * </pre> The two fields are independent: {@code ui.resourceUri} and the flat
	 * {@code ui/resourceUri} alias are emitted only for a non-blank {@code resourceUri},
	 * and {@code ui.visibility} only for a non-empty {@code visibility}, so an app-only
	 * tool without a UI of its own carries just {@code {"ui": {"visibility": ["app"]}}}.
	 * Duplicate visibility values are removed, keeping the first occurrence.
	 * <p>
	 * The CSP is deliberately not part of the tool metadata: per SEP-1865 hosts read it
	 * from the UI resource, see
	 * {@link #buildResourceUiMeta(McpCsp, McpPermission[], String, PrefersBorder)}.
	 * @param resourceUri the {@code ui://} resource URI, may be {@code null} or blank
	 * @param visibility the visibility array from the annotation, may be {@code null} or
	 * empty
	 * @return the {@code _meta} map, or {@code null} if {@code resourceUri} is blank and
	 * {@code visibility} is empty
	 */
	public static Map<String, Object> buildUiMeta(String resourceUri, Visibility[] visibility) {
		boolean hasResourceUri = resourceUri != null && !resourceUri.isBlank();
		boolean hasVisibility = visibility != null && visibility.length > 0;
		if (!hasResourceUri && !hasVisibility) {
			return null;
		}

		var uiMap = new LinkedHashMap<String, Object>();
		if (hasResourceUri) {
			uiMap.put("resourceUri", resourceUri);
		}
		if (hasVisibility) {
			uiMap.put("visibility",
					Arrays.stream(visibility).map(Visibility::getWireValue).distinct().collect(Collectors.toList()));
		}

		var meta = new LinkedHashMap<String, Object>();
		meta.put("ui", uiMap);
		if (hasResourceUri) {
			meta.put("ui/resourceUri", resourceUri);
		}
		return meta;
	}

	/**
	 * Build the {@code _meta} map carrying the SEP-1865 {@code UIResourceMeta} of an MCP
	 * App UI resource: its Content Security Policy, the sandbox permissions requested by
	 * the UI, the dedicated origin for the view and the visual boundary preference.
	 * <p>
	 * Produces the wire format: <pre>
	 * {
	 *   "ui": {
	 *     "csp": { "connectDomains": [...], ... },
	 *     "permissions": { "camera": {}, "clipboardWrite": {} },
	 *     "domain": "a904794854a047f6.claudemcpcontent.com",
	 *     "prefersBorder": true
	 *   }
	 * }
	 * </pre> Fields are emitted in specification order and only when declared:
	 * {@code csp} with the non-empty CSP fields, {@code permissions} for a non-empty
	 * {@code permissions} array (deduplicated, in {@link McpPermission} declaration
	 * order), {@code domain} for a non-blank {@code domain} and {@code prefersBorder}
	 * unless it is {@link PrefersBorder#UNSET}. A {@code null} argument is treated as not
	 * declared.
	 * @param csp the CSP annotation, may be {@code null}
	 * @param permissions the sandbox permissions requested by the UI, may be {@code null}
	 * or empty
	 * @param domain the dedicated origin for the view sandbox, whose format is
	 * host-dependent; may be {@code null} or blank
	 * @param prefersBorder the visual boundary preference, may be {@code null}
	 * @return the {@code _meta} map, or {@code null} if none of the fields is declared
	 */
	public static @Nullable Map<String, Object> buildResourceUiMeta(@Nullable McpCsp csp,
			McpPermission @Nullable [] permissions, @Nullable String domain, @Nullable PrefersBorder prefersBorder) {
		var uiMap = new LinkedHashMap<String, Object>();
		Map<String, Object> cspMap = buildCspMap(csp);
		if (cspMap != null) {
			uiMap.put("csp", cspMap);
		}
		if (permissions != null && permissions.length > 0) {
			var permissionsMap = new LinkedHashMap<String, Object>();
			for (McpPermission permission : EnumSet.copyOf(Arrays.asList(permissions))) {
				permissionsMap.put(permission.getWireValue(), Map.of());
			}
			uiMap.put("permissions", permissionsMap);
		}
		if (domain != null && !domain.isBlank()) {
			uiMap.put("domain", domain);
		}
		if (prefersBorder != null && prefersBorder != PrefersBorder.UNSET) {
			uiMap.put("prefersBorder", prefersBorder == PrefersBorder.TRUE);
		}
		if (uiMap.isEmpty()) {
			return null;
		}

		var meta = new LinkedHashMap<String, Object>();
		meta.put("ui", uiMap);
		return meta;
	}

	/**
	 * Build the SEP-1865 {@code csp} map from the non-empty fields of the given CSP
	 * annotation, in specification order.
	 * @param csp the CSP annotation, may be {@code null}
	 * @return the CSP map, or {@code null} if {@code csp} is {@code null} or all of its
	 * fields are empty
	 */
	private static @Nullable Map<String, Object> buildCspMap(@Nullable McpCsp csp) {
		if (csp == null) {
			return null;
		}

		var cspMap = new LinkedHashMap<String, Object>();
		putIfNotEmpty(cspMap, "connectDomains", csp.connectDomains());
		putIfNotEmpty(cspMap, "resourceDomains", csp.resourceDomains());
		putIfNotEmpty(cspMap, "frameDomains", csp.frameDomains());
		putIfNotEmpty(cspMap, "baseUriDomains", csp.baseUriDomains());
		return cspMap.isEmpty() ? null : cspMap;
	}

	private static void putIfNotEmpty(Map<String, Object> target, String key, String[] values) {
		if (values != null && values.length > 0) {
			target.put(key, List.of(values));
		}
	}

	/**
	 * Build the {@code _meta} map of an {@link McpResource}, combining the metadata of
	 * its {@link McpResource#metaProvider() metaProvider} with the SEP-1865 UI resource
	 * metadata declared via {@link McpResource#csp()}, {@link McpResource#permissions()},
	 * {@link McpResource#domain()} and {@link McpResource#prefersBorder()} (see
	 * {@link #buildResourceUiMeta(McpCsp, McpPermission[], String, PrefersBorder)}).
	 * <p>
	 * The result is used both for the {@code resources/list} entry and for the
	 * {@code resources/read} content items. As with {@link #mergeMeta(Map, Map)}, the
	 * {@code metaProvider} wins on conflict, for {@code ui} per nested key.
	 * @param resource the resource annotation
	 * @return the merged {@code _meta} map, or {@code null} if neither source supplies
	 * metadata
	 */
	public static Map<String, Object> getResourceMeta(McpResource resource) {
		return mergeMeta(getMeta(resource.metaProvider()), buildResourceUiMeta(resource.csp(), resource.permissions(),
				resource.domain(), resource.prefersBorder()));
	}

	/**
	 * Merge two metadata maps with provider-wins-on-conflict semantics.
	 * <p>
	 * Top-level keys from both maps are combined. For the {@code "ui"} key specifically,
	 * the nested maps are deep-merged so annotation-derived fields (like
	 * {@code visibility}) survive even when the provider also sets {@code "ui"}. Provider
	 * values win for any key present in both.
	 * @param providerMeta metadata from {@link MetaProvider}, may be {@code null}
	 * @param annotationMeta metadata built from annotation fields, may be {@code null}
	 * @return the merged map, or {@code null} if both inputs are {@code null}
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> mergeMeta(Map<String, Object> providerMeta, Map<String, Object> annotationMeta) {
		if (providerMeta == null && annotationMeta == null) {
			return null;
		}
		if (providerMeta == null) {
			return annotationMeta;
		}
		if (annotationMeta == null) {
			return providerMeta;
		}

		var merged = new LinkedHashMap<String, Object>();
		merged.putAll(annotationMeta);

		for (var entry : providerMeta.entrySet()) {
			String key = entry.getKey();
			Object providerValue = entry.getValue();
			Object existingValue = merged.get(key);

			if ("ui".equals(key) && providerValue instanceof Map && existingValue instanceof Map) {
				var deepMerged = new LinkedHashMap<String, Object>();
				deepMerged.putAll((Map<String, Object>) existingValue);
				deepMerged.putAll((Map<String, Object>) providerValue);
				merged.put(key, deepMerged);
			}
			else {
				merged.put(key, providerValue);
			}
		}

		// Re-derive the flat "ui/resourceUri" alias from the merged nested value so the
		// two never diverge when a MetaProvider overrides ui.resourceUri.
		if (merged.containsKey("ui/resourceUri") && merged.get("ui") instanceof Map<?, ?> uiMap) {
			Object mergedResourceUri = uiMap.get("resourceUri");
			if (mergedResourceUri != null) {
				merged.put("ui/resourceUri", mergedResourceUri);
			}
		}

		return merged;
	}

}
