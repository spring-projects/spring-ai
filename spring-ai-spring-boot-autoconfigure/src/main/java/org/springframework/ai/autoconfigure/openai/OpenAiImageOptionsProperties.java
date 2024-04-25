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
package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.OpenAiImageOptionsBuilder;

/**
 * This class is an object for binding Spring Properties. As it implements the
 * {@link OpenAiImageOptions} interface, it can be used as an immutable object through the
 * {@link OpenAiImageOptionsBuilder}.
 *
 * @since 0.8.1
 * @author youngmon
 */
public class OpenAiImageOptionsProperties implements OpenAiImageOptions {

	private Integer n;

	private String model;

	private Integer width;

	private Integer height;

	private String quality;

	private String responseFormat;

	private String size;

	private String style;

	private String user;

	@Override
	public Integer getN() {
		return this.n;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public Integer getHeight() {
		return this.height;
	}

	@Override
	public Integer getWidth() {
		return this.width;
	}

	@Override
	public String getResponseFormat() {
		return responseFormat;
	}

	@Override
	public String getSize() {

		if (this.size != null) {
			return this.size;
		}
		return (this.width != null && this.height != null) ? this.width + "x" + this.height : null;
	}

	@Override
	public String getUser() {
		return this.user;
	}

	@Override
	public String getStyle() {
		return this.style;
	}

	@Override
	public String getQuality() {
		return this.quality;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setSize(String size) {
		this.size = size;
	}

}
