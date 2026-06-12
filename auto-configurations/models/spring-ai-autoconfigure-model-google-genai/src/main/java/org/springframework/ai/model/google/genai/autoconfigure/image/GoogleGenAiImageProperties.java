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

package org.springframework.ai.model.google.genai.autoconfigure.image;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.google.genai.image.GoogleGenAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google GenAI Image.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiImageProperties.CONFIG_PREFIX)
public class GoogleGenAiImageProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.image";

	private @Nullable String model;

	private @Nullable Integer n;

	private @Nullable String aspectRatio = GoogleGenAiImageOptions.DEFAULT_ASPECT_RATIO;

	private @Nullable Integer seed;

	private GoogleGenAiImageOptions.@Nullable SafetyFilterLevel safetyFilterLevel;

	private GoogleGenAiImageOptions.@Nullable PersonGeneration personGeneration;

	private @Nullable String outputMimeType;

	private @Nullable Integer outputCompressionQuality;

	private @Nullable Map<String, String> labels;

	private @Nullable String imageSize;

	private @Nullable Float temperature;

	private @Nullable Float topP;

	private @Nullable Float topK;

	private @Nullable Integer maxOutputTokens;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	public @Nullable String getAspectRatio() {
		return this.aspectRatio;
	}

	public void setAspectRatio(@Nullable String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public GoogleGenAiImageOptions.@Nullable SafetyFilterLevel getSafetyFilterLevel() {
		return this.safetyFilterLevel;
	}

	public void setSafetyFilterLevel(GoogleGenAiImageOptions.@Nullable SafetyFilterLevel safetyFilterLevel) {
		this.safetyFilterLevel = safetyFilterLevel;
	}

	public GoogleGenAiImageOptions.@Nullable PersonGeneration getPersonGeneration() {
		return this.personGeneration;
	}

	public void setPersonGeneration(GoogleGenAiImageOptions.@Nullable PersonGeneration personGeneration) {
		this.personGeneration = personGeneration;
	}

	public @Nullable String getOutputMimeType() {
		return this.outputMimeType;
	}

	public void setOutputMimeType(@Nullable String outputMimeType) {
		this.outputMimeType = outputMimeType;
	}

	public @Nullable Integer getOutputCompressionQuality() {
		return this.outputCompressionQuality;
	}

	public void setOutputCompressionQuality(@Nullable Integer outputCompressionQuality) {
		this.outputCompressionQuality = outputCompressionQuality;
	}

	public @Nullable Map<String, String> getLabels() {
		return this.labels;
	}

	public void setLabels(@Nullable Map<String, String> labels) {
		this.labels = labels;
	}

	public @Nullable String getImageSize() {
		return this.imageSize;
	}

	public void setImageSize(@Nullable String imageSize) {
		this.imageSize = imageSize;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Float temperature) {
		this.temperature = temperature;
	}

	public @Nullable Float getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Float topP) {
		this.topP = topP;
	}

	public @Nullable Float getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Float topK) {
		this.topK = topK;
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public GoogleGenAiImageOptions toOptions() {
		return GoogleGenAiImageOptions.builder()
			.model(this.model)
			.n(this.n)
			.aspectRatio(this.aspectRatio)
			.seed(this.seed)
			.safetyFilterLevel(this.safetyFilterLevel)
			.personGeneration(this.personGeneration)
			.outputMimeType(this.outputMimeType)
			.outputCompressionQuality(this.outputCompressionQuality)
			.labels(this.labels)
			.imageSize(this.imageSize)
			.temperature(this.temperature)
			.topP(this.topP)
			.topK(this.topK)
			.maxOutputTokens(this.maxOutputTokens)
			.build();
	}

}
