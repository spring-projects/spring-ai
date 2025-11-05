/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Description of an embedding model.
 *
 * @author Christian Tzolov
 * @author Nicolas Krier
 */
public interface EmbeddingModelDescription extends ModelDescription {

	default int getDimensions() {
		return -1;
	}

	static <E extends Enum<E> & EmbeddingModelDescription> Map<String, Integer> calculateKnownEmbeddingDimensions(
			Class<E> embeddingModelClass) {
		return Stream.of(embeddingModelClass.getEnumConstants())
			.collect(Collectors.collectingAndThen(
					Collectors.toMap(ModelDescription::getName, EmbeddingModelDescription::getDimensions),
					Map::copyOf));
	}

}
