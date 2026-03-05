/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.context;

import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;

import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.LoggingSpec;

/**
 * @author Christian Tzolov
 */
public class DefaultLoggingSpec implements LoggingSpec {

	protected String message;

	protected String logger;

	protected LoggingLevel level = LoggingLevel.INFO;

	protected Map<String, Object> meta = new HashMap<>();

	@Override
	public LoggingSpec message(String message) {
		this.message = message;
		return this;
	}

	@Override
	public LoggingSpec logger(String logger) {
		this.logger = logger;
		return this;
	}

	@Override
	public LoggingSpec level(LoggingLevel level) {
		this.level = level;
		return this;
	}

	@Override
	public LoggingSpec meta(Map<String, Object> m) {
		if (m != null) {
			this.meta.putAll(m);
		}
		return this;
	}

	@Override
	public LoggingSpec meta(String k, Object v) {
		if (k != null && v != null) {
			this.meta.put(k, v);
		}
		return this;
	}

}
