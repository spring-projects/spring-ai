/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.qianfan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * This class represents the options for QianFan embedding.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0
 */
@JsonInclude(Include.NON_NULL)
public class QianFanEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;

	/**
	 * A unique identifier representing your end-user, which can help QianFan to
	 * monitor and detect abuse.
	 */
	private @JsonProperty("user_id") String user;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected QianFanEmbeddingOptions options;

		public Builder() {
			this.options = new QianFanEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withUser(String user) {
			this.options.setUser(user);
			return this;
		}

		public QianFanEmbeddingOptions build() {
			return this.options;
		}

	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

}
