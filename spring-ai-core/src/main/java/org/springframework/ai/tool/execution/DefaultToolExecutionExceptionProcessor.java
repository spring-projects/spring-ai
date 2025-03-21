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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolExecutionExceptionProcessor}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultToolExecutionExceptionProcessor implements ToolExecutionExceptionProcessor {

	private final static Logger logger = LoggerFactory.getLogger(DefaultToolExecutionExceptionProcessor.class);

	private static final boolean DEFAULT_ALWAYS_THROW = false;

	private final boolean alwaysThrow;

	public DefaultToolExecutionExceptionProcessor(boolean alwaysThrow) {
		this.alwaysThrow = alwaysThrow;
	}

	@Override
	public String process(ToolExecutionException exception) {
		Assert.notNull(exception, "exception cannot be null");
		if (alwaysThrow) {
			throw exception;
		}
		logger.debug("Exception thrown by tool: {}. Message: {}", exception.getToolDefinition().name(),
				exception.getMessage());
		return exception.getMessage();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean alwaysThrow = DEFAULT_ALWAYS_THROW;

		public Builder alwaysThrow(boolean alwaysThrow) {
			this.alwaysThrow = alwaysThrow;
			return this;
		}

		public DefaultToolExecutionExceptionProcessor build() {
			return new DefaultToolExecutionExceptionProcessor(alwaysThrow);
		}

	}

}
