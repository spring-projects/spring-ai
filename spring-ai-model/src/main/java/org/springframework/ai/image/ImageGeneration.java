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

package org.springframework.ai.image;

import org.springframework.ai.model.ModelResult;

public class ImageGeneration implements ModelResult<Image> {

	private static final ImageGenerationMetadata NONE = new ImageGenerationMetadata() {

	};

	private final ImageGenerationMetadata imageGenerationMetadata;

	private final Image image;

	public ImageGeneration(Image image) {
		this(image, NONE);
	}

	public ImageGeneration(Image image, ImageGenerationMetadata imageGenerationMetadata) {
		this.image = image;
		this.imageGenerationMetadata = imageGenerationMetadata;
	}

	@Override
	public Image getOutput() {
		return this.image;
	}

	@Override
	public ImageGenerationMetadata getMetadata() {
		return this.imageGenerationMetadata;
	}

	@Override
	public String toString() {
		return "ImageGeneration{" + "imageGenerationMetadata=" + this.imageGenerationMetadata + ", image=" + this.image
				+ '}';
	}

}
