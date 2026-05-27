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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpCsp;
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
	 *   "ui": { "resourceUri": "ui://...", "visibility": ["model"], "csp": {...} },
	 *   "ui/resourceUri": "ui://..."
	 * }
	 * </pre>
	 * @param resourceUri the {@code ui://} resource URI; if blank, returns {@code null}
	 * @param visibility the visibility array from the annotation
	 * @param csp the CSP annotation, or {@code null}
	 * @return the {@code _meta} map, or {@code null} if no SEP-1865 fields are set
	 */
	public static Map<String, Object> buildUiMeta(String resourceUri, Visibility[] visibility, McpCsp csp) {
		if (resourceUri == null || resourceUri.isEmpty()) {
			return null;
		}

		var uiMap = new LinkedHashMap<String, Object>();
		uiMap.put("resourceUri", resourceUri);

		if (visibility != null && visibility.length > 0) {
			uiMap.put("visibility",
					Arrays.stream(visibility).map(Visibility::getWireValue).collect(Collectors.toList()));
		}

		if (csp != null) {
			var cspMap = new LinkedHashMap<String, Object>();
			if (csp.connectDomains().length > 0) {
				cspMap.put("connectDomains", List.of(csp.connectDomains()));
			}
			if (csp.resourceDomains().length > 0) {
				cspMap.put("resourceDomains", List.of(csp.resourceDomains()));
			}
			if (csp.redirectDomains().length > 0) {
				cspMap.put("redirectDomains", List.of(csp.redirectDomains()));
			}
			if (!cspMap.isEmpty()) {
				uiMap.put("csp", cspMap);
			}
		}

		var meta = new LinkedHashMap<String, Object>();
		meta.put("ui", uiMap);
		meta.put("ui/resourceUri", resourceUri);
		return meta;
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

		return merged;
	}

}
