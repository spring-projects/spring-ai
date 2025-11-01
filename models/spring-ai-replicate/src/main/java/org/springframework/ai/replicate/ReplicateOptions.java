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

package org.springframework.ai.replicate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.model.ModelOptions;

/**
 * Base options for Replicate models. Contains common fields that apply to all Replicate
 * models regardless of type (chat, image, audio, etc.).
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplicateOptions implements ModelOptions {

	/**
	 * The model identifier in format "owner/model-name" (e.g., "meta/llama-2-70b-chat")
	 */
	protected String model;

	/**
	 * The specific version hash of the model to use. Not mandatory for "official" models.
	 */
	@JsonProperty("version")
	protected String version;

	/**
	 * Flexible input map containing model-specific parameters. This allows support for
	 * any model on Replicate, regardless of its specific input schema.
	 */
	@JsonProperty("input")
	protected Map<String, Object> input = new HashMap<>();

	/**
	 * Optional webhook URL for async notifications
	 */
	@JsonProperty("webhook")
	protected String webhook;

	/**
	 * Optional webhook events to subscribe to
	 */
	@JsonProperty("webhook_events_filter")
	protected List<String> webhookEventsFilter;

	public ReplicateOptions() {
	}

	protected ReplicateOptions(Builder builder) {
		this.model = builder.model;
		this.version = builder.version;
		this.input = builder.input != null ? new HashMap<>(builder.input) : new HashMap<>();
		this.webhook = builder.webhook;
		this.webhookEventsFilter = builder.webhookEventsFilter;
	}

	/**
	 * Add a custom parameter to the model input
	 */
	public ReplicateOptions withParameter(String key, Object value) {
		this.input.put(key, value);
		return this;
	}

	/**
	 * Add multiple parameters to the model input
	 */
	public ReplicateOptions withParameters(Map<String, Object> parameters) {
		this.input.putAll(parameters);
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a new ReplicateOptions from existing options
	 */
	public static ReplicateOptions fromOptions(ReplicateOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.version(fromOptions.getVersion())
			.input(new HashMap<>(fromOptions.getInput()))
			.webhook(fromOptions.getWebhook())
			.webhookEventsFilter(fromOptions.getWebhookEventsFilter())
			.build();
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Map<String, Object> getInput() {
		return this.input;
	}

	public void setInput(Map<String, Object> input) {
		this.input = ReplicateOptionsUtils.convertMapValues(input);
	}

	public String getWebhook() {
		return this.webhook;
	}

	public void setWebhook(String webhook) {
		this.webhook = webhook;
	}

	public List<String> getWebhookEventsFilter() {
		return this.webhookEventsFilter;
	}

	public void setWebhookEventsFilter(List<String> webhookEventsFilter) {
		this.webhookEventsFilter = webhookEventsFilter;
	}

	public static class Builder {

		protected String model;

		protected String version;

		protected Map<String, Object> input = new HashMap<>();

		protected String webhook;

		protected List<String> webhookEventsFilter;

		protected Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		private Builder input(Map<String, Object> input) {
			this.input = input;
			return this;
		}

		public Builder withParameter(String key, Object value) {
			this.input.put(key, value);
			return this;
		}

		public Builder withParameters(Map<String, Object> params) {
			this.input.putAll(params);
			return this;
		}

		public Builder webhook(String webhook) {
			this.webhook = webhook;
			return this;
		}

		public Builder webhookEventsFilter(List<String> webhookEventsFilter) {
			this.webhookEventsFilter = webhookEventsFilter;
			return this;
		}

		public ReplicateOptions build() {
			return new ReplicateOptions(this);
		}

	}

}
