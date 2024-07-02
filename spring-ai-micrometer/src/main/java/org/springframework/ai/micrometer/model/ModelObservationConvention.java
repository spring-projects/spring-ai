package org.springframework.ai.micrometer.model;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} for {@link ModelObservationContext}.
 *
 * @author Thomas Vitale
 */
public interface ModelObservationConvention extends ObservationConvention<ModelObservationContext> {

	@Override
	default boolean supportsContext(Observation.Context context) {
		return context instanceof ModelObservationContext;
	}

}
