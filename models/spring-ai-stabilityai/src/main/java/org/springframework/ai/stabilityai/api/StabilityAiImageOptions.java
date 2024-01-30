/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.stabilityai.api;

import org.springframework.ai.image.ImageOptions;

/**
 * StabilityAiImageOptions is an interface that extends ImageOptions. It provides
 * additional stability AI specific image options.
 */
public interface StabilityAiImageOptions extends ImageOptions {

	Float getCfgScale();

	String getClipGuidancePreset();

	String getSampler();

	Integer getSamples();

	Long getSeed();

	Integer getSteps();

	String getStylePreset();

	// extras json object...

}
