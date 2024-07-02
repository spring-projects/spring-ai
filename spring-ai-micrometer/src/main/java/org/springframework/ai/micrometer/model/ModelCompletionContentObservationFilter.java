package org.springframework.ai.micrometer.model;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * An {@link ObservationFilter} to include the completion content in the observation.
 *
 * @author Thomas Vitale
 */
public class ModelCompletionContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ModelObservationContext modelObservationContext)) {
			return context;
		}

		if (modelObservationContext.getModelResponseContext() == null
				|| modelObservationContext.getModelResponseContext().completion() == null) {
			return modelObservationContext;
		}

		modelObservationContext.addHighCardinalityKeyValue(ModelObservation.ContentHighCardinalityKeyNames.COMPLETION
			.withValue(modelObservationContext.getModelResponseContext().completion()));

		return modelObservationContext;
	}

}
