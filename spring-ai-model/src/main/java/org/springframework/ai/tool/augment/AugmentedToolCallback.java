/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.tool.augment;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.augment.ToolInputSchemaAugmenter.AugmentedArgumentType;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * This class wraps an existing {@link ToolCallback} and modifies its input schema to
 * include additional fields defined in the provided Record type. It also provides a
 * mechanism to handle these extended arguments, either by consuming them via a provided
 * {@link Consumer} or by removing them from the input after processing.
 *
 * @author Christian Tzolov
 */
public class AugmentedToolCallback<T extends Record> implements ToolCallback {

	/**
	 * The delegate ToolCallback that this class extends.
	 */
	private final ToolCallback delegate;

	/**
	 * The augmented ToolDefinition that includes the augmented input schema.
	 */
	private ToolDefinition augmentedToolDefinition;

	/**
	 * The record class type that defines the structure of the augmented arguments.
	 */
	private Class<T> augmentedArgumentsClass;

	/**
	 * A consumer that processes the augmented arguments extracted from the tool input.
	 */
	private Consumer<AugmentedArgumentEvent<T>> augmentedArgumentsConsumer;

	/**
	 * The list of tool argument types that have been added to the tool input schema.
	 */
	private List<AugmentedArgumentType> augmentedArgumentTypes;

	/**
	 * A flag indicating whether to remove the augmented arguments from the tool input
	 * after they have been processed. If the arguments are not removed, they will remain
	 * in the tool input for the delegate to process. In many cases this could be useful.
	 */
	private boolean removeAugmentedArgumentsAfterProcessing = false;

	public AugmentedToolCallback(ToolCallback delegate, Class<T> augmentedArgumentsClass,
			Consumer<AugmentedArgumentEvent<T>> augmentedArgumentsConsumer,
			boolean removeExtraArgumentsAfterProcessing) {
		Assert.notNull(delegate, "Delegate ToolCallback must not be null");
		Assert.notNull(augmentedArgumentsClass, "Argument types must not be null");
		Assert.isTrue(augmentedArgumentsClass.isRecord(), "Argument types must be a Record type");
		Assert.isTrue(augmentedArgumentsClass.getRecordComponents().length > 0,
				"Argument types must have at least one field");

		this.delegate = delegate;
		this.augmentedArgumentTypes = ToolInputSchemaAugmenter.toAugmentedArgumentTypes(augmentedArgumentsClass);
		String originalSchema = this.delegate.getToolDefinition().inputSchema();
		String augmentedSchema = ToolInputSchemaAugmenter.augmentToolInputSchema(originalSchema,
				this.augmentedArgumentTypes);
		this.augmentedToolDefinition = ToolDefinition.builder()
			.name(this.delegate.getToolDefinition().name())
			.description(this.delegate.getToolDefinition().description())
			.inputSchema(augmentedSchema)
			.build();

		this.augmentedArgumentsClass = augmentedArgumentsClass;
		this.augmentedArgumentsConsumer = augmentedArgumentsConsumer;
		this.removeAugmentedArgumentsAfterProcessing = removeExtraArgumentsAfterProcessing;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.augmentedToolDefinition;
	}

	@Override
	public String call(String toolInput) {
		return this.delegate.call(this.handleAugmentedArguments(toolInput));
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext tooContext) {
		return this.delegate.call(this.handleAugmentedArguments(toolInput), tooContext);
	}

	/**
	 * Handles the augmented arguments in the tool input. It extracts the augmented
	 * arguments from the tool input, processes them using the provided consumer, and
	 * optionally removes them from the tool input.
	 * @param toolInput the input as received from the LLM.
	 * @return the input to send to the delegate ToolCallback
	 */
	private String handleAugmentedArguments(String toolInput) {

		// Extract the augmented arguments from the toolInput and send them to the
		// consumer if provided.
		if (this.augmentedArgumentsConsumer != null) {
			T augmentedArguments = JsonParser.fromJson(toolInput, this.augmentedArgumentsClass);
			this.augmentedArgumentsConsumer
				.accept(new AugmentedArgumentEvent<>(this.augmentedToolDefinition, toolInput, augmentedArguments));
		}

		// Optionally remove the extra arguments from the toolInput
		if (this.removeAugmentedArgumentsAfterProcessing) {
			var args = JsonParser.fromJson(toolInput, new TypeReference<Map<String, Object>>() {
			});

			for (AugmentedArgumentType newFieldType : this.augmentedArgumentTypes) {
				args.remove(newFieldType.name());
			}
			toolInput = JsonParser.toJson(args);
		}

		return toolInput;
	}

}
