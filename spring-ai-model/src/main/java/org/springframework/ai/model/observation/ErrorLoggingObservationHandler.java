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

package org.springframework.ai.model.observation;

import java.util.List;
import java.util.function.Consumer;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * An {@link ObservationHandler} that logs errors using a {@link Tracer}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@SuppressWarnings({ "rawtypes", "null" })
public class ErrorLoggingObservationHandler implements ObservationHandler {

	private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingObservationHandler.class);

	private final Tracer tracer;

	private final List<Class<? extends Observation.Context>> supportedContextTypes;

	private final Consumer<Context> errorConsumer;

	public ErrorLoggingObservationHandler(Tracer tracer,
			List<Class<? extends Observation.Context>> supportedContextTypes) {
		this(tracer, supportedContextTypes, context -> logger.error("Traced Error: ", context.getError()));
	}

	public ErrorLoggingObservationHandler(Tracer tracer,
			List<Class<? extends Observation.Context>> supportedContextTypes, Consumer<Context> errorConsumer) {

		Assert.notNull(tracer, "Tracer must not be null");
		Assert.notNull(supportedContextTypes, "SupportedContextTypes must not be null");
		Assert.notNull(errorConsumer, "ErrorConsumer must not be null");

		this.tracer = tracer;
		this.supportedContextTypes = supportedContextTypes;
		this.errorConsumer = errorConsumer;
	}

	@Override
	public boolean supportsContext(Context context) {
		return (context == null) ? false : this.supportedContextTypes.stream().anyMatch(clz -> clz.isInstance(context));
	}

	@Override
	public void onError(Context context) {
		if (context != null) {
			TracingContext tracingContext = context.get(TracingContext.class);
			if (tracingContext != null) {
				try (var val = this.tracer.withSpan(tracingContext.getSpan())) {
					this.errorConsumer.accept(context);
				}
			}
		}
	}

}
