/*
 * Copyright 2023-2023 the original author or authors.
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

import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 * @author Christian Tzolov
 */
public class ModelOptionsUtils {

	private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final static List<String> FIELD_EXCISIONS = List.of("class", "commonTemperature");

	/**
	 * Merges the source object into the target object. The source null values are
	 * ignored. Only objects with Getter and Setter methods are supported.
	 * @param <T> the type of the source and target object.
	 * @param source the source object to merge.
	 * @param target the target object to merge into.
	 * @return the merged target object.
	 */
	public static <T> T merge(T source, T target) {
		BeanWrapper srcWrap = new BeanWrapperImpl(source);
		BeanWrapper trgWrap = new BeanWrapperImpl(target);

		for (PropertyDescriptor descriptor : srcWrap.getPropertyDescriptors()) {
			if (!FIELD_EXCISIONS.contains(descriptor.getName())) {
				String propertyName = descriptor.getName();
				Object value = srcWrap.getPropertyValue(propertyName);

				// Copy value to the target object
				if (value != null) {
					trgWrap.setPropertyValue(propertyName, value);
				}
			}
		}

		return target;
	}

	/**
	 * Converts the given object to a Map.
	 * @param source the object to convert to a Map.
	 * @return the converted Map.
	 */
	public static Map<String, Object> objectToMap(Object source) {
		try {
			String json = OBJECT_MAPPER.writeValueAsString(source);
			return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts the given Map to the given class.
	 * @param <T> the type of the class to return.
	 * @param source the Map to convert to the given class.
	 * @param clazz the class to convert the Map to.
	 * @return the converted class.
	 */
	public static <T> T mapToClass(Map<String, Object> source, Class<T> clazz) {
		try {
			String json = OBJECT_MAPPER.writeValueAsString(source);
			return OBJECT_MAPPER.readValue(json, clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Merges the source object into the target object and returns an object represented
	 * by the given class. The source null values are ignored.
	 * @param <T> they type of the class to return.
	 * @param source the source object to merge.
	 * @param target the target object to merge into.
	 * @param clazz the class to return.
	 * @return the merged object represented by the given class.
	 */
	public static <T> T merge(Object source, Object target, Class<T> clazz) {
		Map<String, Object> sourceMap = objectToMap(source);
		Map<String, Object> targetMap = objectToMap(target);

		targetMap.putAll(sourceMap.entrySet()
			.stream()
			.filter(e -> e.getValue() != null)
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));

		return mapToClass(targetMap, clazz);
	}

	private static AtomicReference<SchemaGenerator> GENERATOR_CACHE = new AtomicReference<>();

	/**
	 * Generates JSON Schema (version 2020_12) for the given class.
	 * @param clazz the class to generate JSON Schema for.
	 * @return the generated JSON Schema as a String.
	 */
	public static String getJsonSchema(Class<?> clazz) {

		if (GENERATOR_CACHE.get() == null) {

			JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);

			SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
					OptionPreset.PLAIN_JSON)
				.with(jacksonModule);

			SchemaGeneratorConfig config = configBuilder.build();
			SchemaGenerator generator = new SchemaGenerator(config);
			GENERATOR_CACHE.compareAndSet(null, generator);
		}

		return GENERATOR_CACHE.get().generateSchema(clazz).toPrettyString();
	}

	/**
	 * Converts input JSON string to the target class.
	 * @param <T> the target class type.
	 * @param json the input JSON string.
	 * @param targetClass the target class to convert the JSON to.
	 * @return the converted target class instance.
	 */
	public static <T> T fromJson(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
