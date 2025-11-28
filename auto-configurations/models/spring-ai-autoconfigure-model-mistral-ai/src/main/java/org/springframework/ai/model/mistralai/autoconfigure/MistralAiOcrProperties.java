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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.springframework.ai.mistralai.ocr.MistralAiOcrOptions;
import org.springframework.ai.mistralai.ocr.MistralOcrApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Mistral AI OCR.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@ConfigurationProperties(MistralAiOcrProperties.CONFIG_PREFIX)
public class MistralAiOcrProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.ocr";

	public static final String DEFAULT_OCR_MODEL = MistralOcrApi.OCRModel.MISTRAL_OCR_LATEST.getValue();

	@NestedConfigurationProperty
	private final MistralAiOcrOptions options = MistralAiOcrOptions.builder().model(DEFAULT_OCR_MODEL).build();

	public MistralAiOcrProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MistralAiOcrOptions getOptions() {
		return this.options;
	}

}
