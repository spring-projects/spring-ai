/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for JSON processing. Provides methods for converting JSON strings to maps
 * and lists, and for converting between lists of different numeric types.
 *
 * @author Christian Tzolov
 */
public class JsonUtils {

	/**
	 * Converts a JSON string to a map.
	 * @param jsonText the JSON string to convert
	 * @return the map representation of the JSON string
	 * @throws RuntimeException if an error occurs during the conversion
	 */
	public static Map<String, Object> jsonToMap(String jsonText) {
		try {
			return (Map<String, Object>) new ObjectMapper().readValue(jsonText, Map.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts a list of doubles to a list of floats.
	 * @param embeddingDouble the list of doubles to convert
	 * @return the list of floats
	 */
	public static List<Float> toFloatList(List<Double> embeddingDouble) {
		return embeddingDouble.stream().map(Number::floatValue).toList();
	}

	/**
	 * Converts a list of doubles to a float array.
	 * @param embeddingDouble the list of doubles to convert
	 * @return the float array
	 */
	public static float[] toFloatArray(List<Double> embeddingDouble) {
		float[] embeddingFloat = new float[embeddingDouble.size()];
		int i = 0;
		for (Double d : embeddingDouble) {
			embeddingFloat[i++] = d.floatValue();
		}
		return embeddingFloat;
	}

	/**
	 * Converts a list of floats to a list of doubles.
	 * @param floats the list of floats to convert
	 * @return the list of doubles
	 */
	public static List<Double> toDouble(List<Float> floats) {
		return floats.stream().map(f -> f.doubleValue()).toList();
	}

}
