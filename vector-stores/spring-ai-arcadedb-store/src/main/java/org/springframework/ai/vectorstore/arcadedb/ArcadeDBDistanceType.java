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

package org.springframework.ai.vectorstore.arcadedb;

import com.github.jelmerk.knn.DistanceFunction;
import com.github.jelmerk.knn.DistanceFunctions;

/**
 * Distance metric types supported by ArcadeDB's HNSW vector index.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
public enum ArcadeDBDistanceType {

	COSINE {
		@Override
		public DistanceFunction<float[], Float> getDistanceFunction() {
			return DistanceFunctions.FLOAT_COSINE_DISTANCE;
		}

		@Override
		public double toSimilarity(double distance) {
			// Cosine distance can slightly exceed 1.0 due to floating-point
			// precision
			return Math.max(0.0, 1.0 - distance);
		}
	},

	EUCLIDEAN {
		@Override
		public DistanceFunction<float[], Float> getDistanceFunction() {
			return DistanceFunctions.FLOAT_EUCLIDEAN_DISTANCE;
		}

		@Override
		public double toSimilarity(double distance) {
			return 1.0 / (1.0 + distance);
		}
	};

	public abstract DistanceFunction<float[], Float> getDistanceFunction();

	public abstract double toSimilarity(double distance);

}
