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

package org.springframework.ai.model.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.Resource;

/**
 * Mixin interface for ChatModels that support structured output. Provides a unified way
 * to set and get the output JSON schema.
 *
 * @author Christian Tzolov
 * @author Nicolas Krier
 */
public interface StructuredOutputChatOptions extends ChatOptions {

	@Nullable String getOutputSchema();

	void setOutputSchema(String outputSchema);

	/**
	 * <p>
	 * Set output schema resource. It can be useful to define the output schema in the
	 * application properties, for example:
	 * {@code spring.ai.ollama.chat.options.output-schema-resource=classpath:schemas/country-json-schema.json}.
	 * </p>
	 * <p>
	 * Note: A default method can't be defined here because {@code JavaBeanBinder} doesn't
	 * take into account interface default methods and {@code StructuredOutputChatOptions}
	 * can't be an abstract class because some vendors have defined abstract classes like
	 * {@code AbstractOpenAiOptions}.
	 * </p>
	 * @param outputSchemaResource The {@link Resource} representing the output schema.
	 */
	void setOutputSchemaResource(Resource outputSchemaResource);

	static String extractOutputSchema(Resource outputSchemaResource) {
		try {
			return outputSchemaResource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to extract output schema content!", exception);
		}
	}

	interface Builder<B extends Builder<B>> extends ChatOptions.Builder<B> {

		B outputSchema(@Nullable String outputSchema);

		default B outputSchemaResource(@Nullable Resource outputSchemaResource) {
			var outputSchema = outputSchemaResource == null ? null : extractOutputSchema(outputSchemaResource);

			return outputSchema(outputSchema);
		}

	}

}
