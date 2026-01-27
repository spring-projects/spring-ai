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

package org.springframework.ai.tool.execution;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.util.json.JsonParser;

/**
 * A default implementation of {@link ToolCallResultConverter}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class DefaultToolCallResultConverter implements ToolCallResultConverter {

	private static final Logger logger = LoggerFactory.getLogger(DefaultToolCallResultConverter.class);

	@Override
	public ToolCallResult convert(@Nullable Object result, @Nullable Type returnType) {
		if (returnType == Void.TYPE) {
			logger.debug("The tool has no return type. Converting to conventional response.");
			return ToolCallResult.builder().content(JsonParser.toJson("Done")).build();
		}
		if (result instanceof RenderedImage) {
			final var buf = new ByteArrayOutputStream(1024 * 4);
			try {
				ImageIO.write((RenderedImage) result, "PNG", buf);
			}
			catch (IOException e) {
				return ToolCallResult.builder()
					.content("Failed to convert tool result to a base64 image: " + e.getMessage())
					.build();
			}
			final var imgB64 = Base64.getEncoder().encodeToString(buf.toByteArray());
			return ToolCallResult.builder()
				.content(JsonParser.toJson(Map.of("mimeType", "image/png", "data", imgB64)))
				.build();
		}
		else {
			logger.debug("Converting tool result to JSON.");
			return ToolCallResult.builder().content(JsonParser.toJson(result)).build();
		}
	}

}
