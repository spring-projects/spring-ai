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
package org.springframework.ai.autoconfigure.qianfan;

import org.springframework.ai.qianfan.QianFanImageOptions;
import org.springframework.ai.qianfan.api.QianFanImageApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * QianFan Image autoconfiguration properties.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(QianFanImageProperties.CONFIG_PREFIX)
public class QianFanImageProperties extends QianFanParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.qianfan.image";

	public static final String DEFAULT_IMAGE_MODEL = QianFanImageApi.ImageModel.Stable_Diffusion_XL.getValue();

	/**
	 * Enable QianFan image model.
	 */
	private boolean enabled = true;

	/**
	 * Options for QianFan Image API.
	 */
	@NestedConfigurationProperty
	private QianFanImageOptions options = QianFanImageOptions.builder().withModel(DEFAULT_IMAGE_MODEL).build();

	public QianFanImageOptions getOptions() {
		return options;
	}

	public void setOptions(QianFanImageOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
