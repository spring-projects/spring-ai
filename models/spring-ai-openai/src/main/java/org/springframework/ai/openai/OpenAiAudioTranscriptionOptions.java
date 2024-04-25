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
package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest.GranularityType;

/**
 * @author youngmon
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @since 0.8.1
 */
@JsonInclude(Include.NON_NULL)
public interface OpenAiAudioTranscriptionOptions extends ModelOptions {

	/**
	 * ID of the model to use.
	 */
	@JsonProperty("model")
	String getModel();

	/**
	 * The format of the transcript output, in one of these options: json, text, srt,
	 * verbose_json, or vtt.
	 */
	@JsonProperty("response_format")
	TranscriptResponseFormat getResponseFormat();

	@JsonProperty("prompt")
	String getPrompt();

	@JsonProperty("language")
	String getLanguage();

	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make
	 * the output more random, while lower values like 0.2 will make it more focused and
	 * deterministic.
	 */
	@JsonProperty("temperature")
	Float getTemperature();

	@JsonProperty("timestamp_granularities")
	GranularityType getGranularityType();

}
