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

package org.springframework.ai.tool.execution;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;

/**
 * A default implementation of {@link ToolCallResultConverter}.
 *
 * @author Thomas Vitale
 * @author Jewoo Shin
 * @since 1.0.0
 */
public final class DefaultToolCallResultConverter implements ToolCallResultConverter {

	private static final Log logger = LogFactory.getLog(DefaultToolCallResultConverter.class);

	private final JsonHelper jsonHelper;

	public DefaultToolCallResultConverter() {
		this.jsonHelper = new JsonHelper();
	}

	/**
	 * Create a converter that serializes tool results using the given {@link JsonMapper}.
	 * <p>
	 * Use this constructor to apply a customized {@code JsonMapper} (e.g. one that
	 * suppresses {@code null} properties) to the JSON produced for tool results, without
	 * replacing the conversion logic of the default converter.
	 * @param jsonMapper the {@link JsonMapper} to use for JSON serialization
	 * @since 2.0.0
	 */
	public DefaultToolCallResultConverter(JsonMapper jsonMapper) {
		Assert.notNull(jsonMapper, "jsonMapper cannot be null");
		this.jsonHelper = new JsonHelper(jsonMapper);
	}

	@Override
	public String convert(@Nullable Object result, @Nullable Type returnType) {
		if (returnType == Void.TYPE) {
			logger.debug("The tool has no return type. Converting to conventional response.");
			return this.jsonHelper.toJson("Done");
		}
		if (result instanceof RenderedImage) {
			final var buf = new ByteArrayOutputStream(1024 * 4);
			try {
				ImageIO.write((RenderedImage) result, "PNG", buf);
			}
			catch (IOException e) {
				return "Failed to convert tool result to a base64 image: " + e.getMessage();
			}
			final var imgB64 = Base64.getEncoder().encodeToString(buf.toByteArray());
			return this.jsonHelper.toJson(Map.of("mimeType", "image/png", "data", imgB64));
		}
		else {
			logger.debug("Converting tool result to JSON.");
			return this.jsonHelper.toJson(result, true);
		}
	}

}
