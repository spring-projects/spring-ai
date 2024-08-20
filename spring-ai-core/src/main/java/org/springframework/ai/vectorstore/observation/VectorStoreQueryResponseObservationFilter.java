/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.vectorstore.observation;

import java.util.StringJoiner;

import org.springframework.util.CollectionUtils;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * An {@link ObservationFilter} to include the Vector Store search response content in the
 * observation.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class VectorStoreQueryResponseObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {

		if (!(context instanceof VectorStoreObservationContext observationContext)) {
			return context;
		}

		if (CollectionUtils.isEmpty(observationContext.getQueryResponse())) {
			return observationContext;
		}

		StringJoiner joiner = new StringJoiner(", ", "[", "]");
		observationContext.getQueryResponse().forEach(document -> joiner.add("\"" + document.getContent() + "\""));

		observationContext
			.addHighCardinalityKeyValue(VectorStoreObservationDocumentation.HighCardinalityKeyNames.QUERY_RESPONSE
				.withValue(joiner.toString()));

		return observationContext;
	}

}
