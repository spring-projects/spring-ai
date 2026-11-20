/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.systemone;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DefaultDecisionOptions;

/**
 * Options for a {@link SystemOneDecisionModel}: the model name and optional chat template
 * keyword arguments. The connection, that is the base URL, the API key, the timeout and
 * the retry policy, belongs to the {@code SystemOneClient} the model is built with.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneDecisionOptions extends DefaultDecisionOptions {

	private final @Nullable Map<String, Object> chatTemplateKwargs;

	protected SystemOneDecisionOptions(@Nullable String model) {
		this(model, null);
	}

	protected SystemOneDecisionOptions(@Nullable String model, @Nullable Map<String, Object> chatTemplateKwargs) {
		super(model);
		this.chatTemplateKwargs = chatTemplateKwargs != null
				? Collections.unmodifiableMap(new LinkedHashMap<>(chatTemplateKwargs)) : null;
	}

	public @Nullable Map<String, Object> getChatTemplateKwargs() {
		return this.chatTemplateKwargs;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Builder mutate() {
		return builder().model(getModel()).chatTemplateKwargs(this.chatTemplateKwargs);
	}

	@Override
	public String toString() {
		return "SystemOneDecisionOptions{model='" + getModel() + "', chatTemplateKwargs=" + this.chatTemplateKwargs
				+ "}";
	}

	public static class Builder extends DefaultDecisionOptions.Builder<Builder> {

		private @Nullable Map<String, Object> chatTemplateKwargs;

		public Builder chatTemplateKwargs(@Nullable Map<String, Object> chatTemplateKwargs) {
			this.chatTemplateKwargs = chatTemplateKwargs != null ? new LinkedHashMap<>(chatTemplateKwargs) : null;
			return this;
		}

		@Override
		public SystemOneDecisionOptions build() {
			return new SystemOneDecisionOptions(this.model, this.chatTemplateKwargs);
		}

	}

}
