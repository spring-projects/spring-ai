/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for embedding related operations.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */

public final class EmbeddingUtils {

	private static final float[] EMPTY_FLOAT_ARRAY = new float[0];

	private EmbeddingUtils() {

	}

	public static List<Float> doubleToFloat(final List<Double> doubles) {
		return doubles.stream().map(f -> f.floatValue()).toList();
	}

	public static float[] toPrimitive(List<Float> floats) {
		if (floats == null || floats.isEmpty()) {
			return EMPTY_FLOAT_ARRAY;
		}
		final float[] result = new float[floats.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = floats.get(i);
		}
		return result;
	}

	public static float[] toPrimitive(final Float[] array) {
		if (array == null || array.length == 0) {
			return EMPTY_FLOAT_ARRAY;
		}
		final float[] result = new float[array.length];
		for (int i = 0; i < array.length; i++) {
			result[i] = array[i].floatValue();
		}
		return result;
	}

	public static Float[] toFloatArray(final float[] array) {
		if (array == null || array.length == 0) {
			return new Float[0];
		}
		final Float[] result = new Float[array.length];
		for (int i = 0; i < array.length; i++) {
			result[i] = array[i];
		}
		return result;
	}

	public static List<Float> toList(float[] floats) {
		List<Float> output = new ArrayList<>();
		for (float value : floats) {
			output.add(value);
		}
		return output;
	}

}
