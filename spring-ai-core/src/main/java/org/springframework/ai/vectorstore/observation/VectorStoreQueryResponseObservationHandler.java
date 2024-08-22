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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.springframework.ai.observation.conventions.VectorStoreObservationAttributes;
import org.springframework.ai.observation.conventions.VectorStoreObservationEventNames;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.util.CollectionUtils;

/**
 * Handler for including the query response content in the observation as a span event.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class VectorStoreQueryResponseObservationHandler implements ObservationHandler<VectorStoreObservationContext> {

	@Override
	public void onStop(VectorStoreObservationContext context) {
		TracingObservationHandler.TracingContext tracingContext = context
			.get(TracingObservationHandler.TracingContext.class);
		Span otelSpan = TracingHelper.extractOtelSpan(tracingContext);

		var documents = VectorStoreObservationContentProcessor.documents(context);

		if (!CollectionUtils.isEmpty(documents) && otelSpan != null) {
			otelSpan.addEvent(VectorStoreObservationEventNames.CONTENT_QUERY_RESPONSE.value(), Attributes.of(
					AttributeKey.stringArrayKey(VectorStoreObservationAttributes.DB_VECTOR_QUERY_CONTENT.value()),
					documents));
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof VectorStoreObservationContext;
	}

}
