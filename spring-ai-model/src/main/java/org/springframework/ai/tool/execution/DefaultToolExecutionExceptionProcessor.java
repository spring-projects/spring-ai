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

package org.springframework.ai.tool.execution;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolExecutionExceptionProcessor}. Can be configured
 * with an allowlist of exceptions that will be unwrapped from the
 * {@link ToolExecutionException} and rethrown as is.
 *
 * @author Thomas Vitale
 * @author Daniel Garnier-Moiroux
 * @author YunKui Lu
 * @since 1.0.0
 */
public class DefaultToolExecutionExceptionProcessor implements ToolExecutionExceptionProcessor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultToolExecutionExceptionProcessor.class);

	private static final boolean DEFAULT_ALWAYS_THROW = false;

	private final boolean alwaysThrow;

	private final List<Class<? extends RuntimeException>> rethrownExceptions;

	public DefaultToolExecutionExceptionProcessor(boolean alwaysThrow) {
		this(alwaysThrow, Collections.emptyList());
	}

	public DefaultToolExecutionExceptionProcessor(boolean alwaysThrow,
			List<Class<? extends RuntimeException>> rethrownExceptions) {
		this.alwaysThrow = alwaysThrow;
		this.rethrownExceptions = Collections.unmodifiableList(rethrownExceptions);
	}

	@Override
	public String process(ToolExecutionException exception) {
		Assert.notNull(exception, "exception cannot be null");
		Throwable cause = exception.getCause();
		if (cause instanceof RuntimeException runtimeException) {
			if (this.rethrownExceptions.stream().anyMatch(rethrown -> rethrown.isAssignableFrom(cause.getClass()))) {
				throw runtimeException;
			}
		}
		else {
			// If the cause is not a RuntimeException (e.g., IOException,
			// OutOfMemoryError), rethrow the tool exception.
			throw exception;
		}

		if (this.alwaysThrow) {
			throw exception;
		}
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			message = "Exception occurred in tool: " + exception.getToolDefinition().name() + " ("
					+ cause.getClass().getSimpleName() + ")";
		}
		logger.debug("Exception thrown by tool: {}. Message: {}", exception.getToolDefinition().name(), message,
				exception);
		return message;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private boolean alwaysThrow = DEFAULT_ALWAYS_THROW;

		private List<Class<? extends RuntimeException>> exceptions = Collections.emptyList();

		/**
		 * Rethrow the {@link ToolExecutionException}
		 * @param alwaysThrow when true, throws; when false, returns the exception message
		 * @return the builder instance
		 */
		public Builder alwaysThrow(boolean alwaysThrow) {
			this.alwaysThrow = alwaysThrow;
			return this;
		}

		/**
		 * An allowlist of exceptions thrown by tools, which will be unwrapped and
		 * re-thrown without further processing.
		 * @param exceptions the list of exceptions
		 * @return the builder instance
		 */
		public Builder rethrowExceptions(List<Class<? extends RuntimeException>> exceptions) {
			this.exceptions = exceptions;
			return this;
		}

		public DefaultToolExecutionExceptionProcessor build() {
			return new DefaultToolExecutionExceptionProcessor(this.alwaysThrow, this.exceptions);
		}

	}

}
