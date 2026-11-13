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

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.Icon;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpIcon;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.util.StringUtils;

/**
 * Converts {@link McpIcon} annotations into schema icons.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class IconUtils {

	private IconUtils() {
	}

	/**
	 * @return the icons as legacy schema records, or {@code null} when the annotation
	 * declares none, so that the {@code icons} field is omitted from the definition
	 */
	public static @Nullable List<Icon> toIcons(McpIcon[] icons) {
		if (icons.length == 0) {
			return null;
		}
		List<Icon> result = new ArrayList<>(icons.length);
		for (McpIcon icon : icons) {
			Icon.Builder builder = Icon.builder(icon.src());
			if (StringUtils.hasText(icon.mimeType())) {
				builder.mimeType(icon.mimeType());
			}
			if (icon.sizes().length > 0) {
				builder.sizes(List.of(icon.sizes()));
			}
			if (StringUtils.hasText(icon.theme())) {
				builder.theme(icon.theme());
			}
			result.add(builder.build());
		}
		return result;
	}

	/**
	 * @return the icons as modern schema records, or {@code null} when the annotation
	 * declares none
	 */
	public static @Nullable List<io.modelcontextprotocol.modern.McpSchema.Icon> toModernIcons(McpIcon[] icons) {
		List<Icon> legacy = toIcons(icons);
		if (legacy == null) {
			return null;
		}
		return legacy.stream()
			.map(icon -> McpSchemaConverter.convert(icon, io.modelcontextprotocol.modern.McpSchema.Icon.class))
			.toList();
	}

}
