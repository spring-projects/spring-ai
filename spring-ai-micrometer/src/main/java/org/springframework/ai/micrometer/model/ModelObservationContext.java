package org.springframework.ai.micrometer.model;

import org.springframework.ai.micrometer.common.AiObservationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Observation context for large language model interactions.
 *
 * @author Thomas Vitale
 */
public class ModelObservationContext extends AiObservationContext {

	private final ModelRequestContext modelRequestContext;

	@Nullable
	private ModelResponseContext modelResponseContext;

	public ModelObservationContext(ModelRequestContext modelRequestContext) {
		this(modelRequestContext, null);
	}

	public ModelObservationContext(ModelRequestContext modelRequestContext,
			@Nullable ModelResponseContext modelResponseContext) {
		Assert.notNull(modelRequestContext, "modelRequestContext cannot be null");
		this.modelRequestContext = modelRequestContext;
		this.modelResponseContext = modelResponseContext;
	}

	public ModelRequestContext getModelRequestContext() {
		return modelRequestContext;
	}

	@Nullable
	public ModelResponseContext getModelResponseContext() {
		return modelResponseContext;
	}

	public void setModelResponseContext(@Nullable ModelResponseContext modelResponseContext) {
		this.modelResponseContext = modelResponseContext;
	}

}
