/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.model.function;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

/**
 * A {@link FunctionCallback} implementation that invokes a method on a given object. It
 * supports both static and non-static methods.
 *
 * Supports methods with arbitrary number of input parameters and methods with void return
 * type.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class MethodFunctionCallback implements FunctionCallback {

	private static Logger logger = LoggerFactory.getLogger(MethodFunctionCallback.class);

	/**
	 * Object instance that contains the method to be invoked. If the method is static
	 * this object can be null.
	 */
	private final Object functionObject;

	/**
	 * The method to be invoked.
	 */
	private final Method method;

	/**
	 * Description to help the LLM model to understand woth the method does and when to
	 * use it.
	 */
	private final String description;

	/**
	 * Internal ObjectMapper used to serialize/deserialize the method input and output.
	 */
	private final ObjectMapper mapper;

	/**
	 * The JSON schema generated from the method input parameters.
	 */
	private final String inputSchema;

	public MethodFunctionCallback(Object functionObject, Method method, String description, ObjectMapper mapper) {

		Assert.notNull(method, "Method must not be null");
		Assert.notNull(mapper, "ObjectMapper must not be null");
		Assert.hasText(description, "Description must not be empty");

		this.method = method;
		this.description = description;
		this.mapper = mapper;
		this.functionObject = functionObject;

		Assert.isTrue(this.functionObject != null || Modifier.isStatic(this.method.getModifiers()),
				"Function object must be provided for non-static methods!");

		// Generate the JSON schema from the method input parameters
		Map<String, Class<?>> methodParameters = Stream.of(method.getParameters())
			.collect(Collectors.toMap(param -> param.getName(), param -> param.getType()));
		this.inputSchema = generateJsonSchema(methodParameters);

		logger.info("Generated JSON Schema: \n:" + this.inputSchema);
	}

	@Override
	public String getName() {
		return method.getName();
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getInputTypeSchema() {
		return this.inputSchema;
	}

	@Override
	public String call(String functionInput) {

		try {

			Map<String, Object> map = this.mapper.readValue(functionInput, Map.class);

			Object[] methodArgs = Stream.of(this.method.getParameters()).map(parameter -> {
				Object rawValue = map.get(parameter.getName());
				Class<?> type = parameter.getType();
				return this.toJavaType(rawValue, type);
			}).toArray();

			Object response = this.method.invoke(this.functionObject, methodArgs);

			var returnType = this.method.getReturnType();
			if (returnType == Void.TYPE) {
				return "Done";
			}

			if (returnType == Class.class || returnType.isRecord() || returnType == List.class
					|| returnType == Map.class) {
				return ModelOptionsUtils.toJsonString(response);

			}
			return "" + response;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Generates a JSON schema from the given named classes.
	 * @param namedClasses The named classes to generate the schema from.
	 * @return The generated JSON schema.
	 */
	protected String generateJsonSchema(Map<String, Class<?>> namedClasses) {
		try {
			JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(this.mapper);

			ObjectNode rootNode = this.mapper.createObjectNode();
			rootNode.put("$schema", "https://json-schema.org/draft/2020-12/schema");
			rootNode.put("type", "object");
			ObjectNode propertiesNode = rootNode.putObject("properties");

			for (Map.Entry<String, Class<?>> entry : namedClasses.entrySet()) {
				String className = entry.getKey();
				Class<?> clazz = entry.getValue();

				JsonSchema schema = schemaGen.generateSchema(clazz);
				JsonNode schemaNode = this.mapper.valueToTree(schema);
				propertiesNode.set(className, schemaNode);
			}

			return this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts the given value to the specified Java type.
	 * @param value The value to convert.
	 * @param javaType The Java type to convert to.
	 * @return Returns the converted value.
	 */
	protected Object toJavaType(Object value, Class<?> javaType) {

		if (value == null) {
			return null;
		}
		if (javaType == String.class) {
			return value.toString();
		}
		else if (javaType == Integer.class || javaType == int.class) {
			return Integer.parseInt(value.toString());
		}
		else if (javaType == Long.class || javaType == long.class) {
			return Long.parseLong(value.toString());
		}
		else if (javaType == Double.class || javaType == double.class) {
			return Double.parseDouble(value.toString());
		}
		else if (javaType == Float.class || javaType == float.class) {
			return Float.parseFloat(value.toString());
		}
		else if (javaType == Boolean.class || javaType == boolean.class) {
			return Boolean.parseBoolean(value.toString());
		}
		else if (javaType.isEnum()) {
			return Enum.valueOf((Class<Enum>) javaType, value.toString());
		}
		// else if (type == Class.class || type.isRecord()) {
		// return ModelOptionsUtils.mapToClass((Map<String, Object>) value, type);
		// }

		try {
			String json = new ObjectMapper().writeValueAsString(value);
			return this.mapper.readValue(json, javaType);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new {@link Builder} for the {@link MethodFunctionCallback}.
	 * @return The builder.
	 */
	public static MethodFunctionCallback.Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for the {@link MethodFunctionCallback}.
	 */
	public static class Builder {

		private Method method;

		private String description;

		private ObjectMapper mapper = new ObjectMapper();

		private Object functionObject = null;

		public MethodFunctionCallback.Builder withFunctionObject(Object functionObject) {
			this.functionObject = functionObject;
			return this;
		}

		public MethodFunctionCallback.Builder withMethod(Method method) {
			Assert.notNull(method, "Method must not be null");
			this.method = method;
			return this;
		}

		public MethodFunctionCallback.Builder withDescription(String description) {
			Assert.hasText(description, "Description must not be empty");
			this.description = description;
			return this;
		}

		public MethodFunctionCallback.Builder withMapper(ObjectMapper mapper) {
			this.mapper = mapper;
			return this;
		}

		public MethodFunctionCallback build() {
			return new MethodFunctionCallback(this.functionObject, this.method, this.description, this.mapper);
		}

	}

}