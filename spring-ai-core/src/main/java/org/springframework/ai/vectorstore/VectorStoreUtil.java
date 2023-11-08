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

package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Christian Tzolov
 */
public class VectorStoreUtil {

	public static Map<String, Object> jsonToMap(String jsonText) {
		try {
			return (Map<String, Object>) new ObjectMapper().readValue(jsonText, Map.class);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<Float> toFloatList(List<Double> embeddingDouble) {
		return embeddingDouble.stream().map(Number::floatValue).toList();
	}

	public static float[] toFloatArray(List<Double> embeddingDouble) {
		float[] embeddingFloat = new float[embeddingDouble.size()];
		int i = 0;
		for (Double d : embeddingDouble) {
			embeddingFloat[i++] = d.floatValue();
		}
		return embeddingFloat;
	}

	public static List<Double> toDouble(List<Float> floats) {
		return floats.stream().map(f -> f.doubleValue()).toList();
	}

}
