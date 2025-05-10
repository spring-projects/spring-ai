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

package org.springframework.ai.vectorstore.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Handler for emitting the query response content to logs.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class VectorStoreQueryResponseObservationHandler implements ObservationHandler<VectorStoreObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(VectorStoreQueryResponseObservationHandler.class);

	@Override
	public void onStop(VectorStoreObservationContext context) {
		logger.debug("Vector Store Query Response:\n{}", ObservabilityHelper.concatenateStrings(documents(context)));
	}

	private List<String> documents(VectorStoreObservationContext context) {
		if (CollectionUtils.isEmpty(context.getQueryResponse())) {
			return List.of();
		}

		return context.getQueryResponse().stream().map(Document::getText).toList();
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof VectorStoreObservationContext;
	}

}
