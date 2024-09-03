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
package org.springframework.ai.zhipuai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;

import java.util.Objects;

/**
 * ZhiPuAiImageOptions represents the options for image generation using ZhiPuAI image
 * model.
 *
 * <p>
 * It implements the ImageOptions interface, which is portable across different image
 * generation models.
 * </p>
 *
 * <p>
 * Default values:
 * </p>
 * <ul>
 * <li>model: ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL</li>
 * <li>user: null</li>
 * </ul>
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZhiPuAiImageOptions implements ImageOptions {

	/**
	 * The model to use for image generation.
	 */
	@JsonProperty("model")
	private String model = ZhiPuAiImageApi.DEFAULT_IMAGE_MODEL;

	/**
	 * A unique identifier representing your end-user, which can help ZhiPuAI to monitor
	 * and detect abuse. User ID length requirement: minimum of 6 characters, maximum of
	 * 128 characters
	 */
	@JsonProperty("user_id")
	private String user;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final ZhiPuAiImageOptions options;

		private Builder() {
			this.options = new ZhiPuAiImageOptions();
		}

		public Builder withModel(String model) {
			options.setModel(model);
			return this;
		}

		public Builder withUser(String user) {
			options.setUser(user);
			return this;
		}

		public ZhiPuAiImageOptions build() {
			return options;
		}

	}

	@Override
	@JsonIgnore
	public Integer getN() {
		return null;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public Integer getWidth() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getHeight() {
		return null;
	}

	@Override
	@JsonIgnore
	public String getResponseFormat() {
		return null;
	}

	@Override
	@JsonIgnore
	public String getStyle() {
		return null;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ZhiPuAiImageOptions that))
			return false;
		return Objects.equals(model, that.model) && Objects.equals(user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(model, user);
	}

	@Override
	public String toString() {
		return "ZhiPuAiImageOptions{model='" + model + '\'' + ", user='" + user + '\'' + '}';
	}

}
