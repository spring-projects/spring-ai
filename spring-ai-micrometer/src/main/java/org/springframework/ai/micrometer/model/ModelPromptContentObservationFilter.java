package org.springframework.ai.micrometer.model;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * An {@link ObservationFilter} to include the prompt content in the observation.
 *
 * @author Thomas Vitale
 */
public class ModelPromptContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ModelObservationContext modelObservationContext)) {
			return context;
		}

		if (modelObservationContext.getModelRequestContext().prompt() == null) {
			return modelObservationContext;
		}

		modelObservationContext.addHighCardinalityKeyValue(ModelObservation.ContentHighCardinalityKeyNames.PROMPT
			.withValue(modelObservationContext.getModelRequestContext().prompt()));

		return modelObservationContext;
	}

}
