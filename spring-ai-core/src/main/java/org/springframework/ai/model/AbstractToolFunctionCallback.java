/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public abstract class AbstractToolFunctionCallback<I, O> implements ToolFunctionCallback {

	private final String name;

	private final String description;

	private final Class<I> inputType;

	private final String inputTypeSchema;

	private final ObjectMapper objectMapper;

	public AbstractToolFunctionCallback(String name, String description, Class<I> inputType) {
		Assert.notNull(name, "Name must not be null");
		Assert.notNull(description, "Description must not be null");
		Assert.notNull(inputType, "InputType must not be null");
		this.name = name;
		this.description = description;
		this.inputType = inputType;
		this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(inputType);
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getInputTypeSchema() {
		return this.inputTypeSchema;
	}

	@Override
	public String call(String functionArguments) {

		// Convert the tool calls JSON arguments into a Java function request object.
		I request = fromJson(functionArguments, inputType);

		O response = this.doCall(request);

		// extend conversation with function response.
		return this.doResponseToString(response);
	}

	abstract public O doCall(I request);

	public String doResponseToString(O response) {
		return response.toString();
	}

	private <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return this.objectMapper.readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((inputType == null) ? 0 : inputType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractToolFunctionCallback other = (AbstractToolFunctionCallback) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		}
		else if (!name.equals(other.name))
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		}
		else if (!description.equals(other.description))
			return false;
		if (inputType == null) {
			if (other.inputType != null)
				return false;
		}
		else if (!inputType.equals(other.inputType))
			return false;
		return true;
	}

}
