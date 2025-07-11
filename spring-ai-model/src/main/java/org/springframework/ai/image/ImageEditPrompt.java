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

import java.util.Objects;

import org.springframework.ai.model.ModelRequest;

public class ImageEditPrompt implements ModelRequest<ImageEditMessage> {

	private final ImageEditMessage message;

	private ImageEditOptions imageEditModelOptions;

	public ImageEditPrompt(ImageEditMessage message, ImageEditOptions imageEditModelOptions) {
		this.message = message;
		this.imageEditModelOptions = imageEditModelOptions;
	}

	@Override
	public ImageEditMessage getInstructions() {
		return message;
	}

	@Override
	public ImageEditOptions getOptions() {
		return this.imageEditModelOptions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ImageEditPrompt that = (ImageEditPrompt) o;
		return Objects.equals(message, that.message)
				&& Objects.equals(imageEditModelOptions, that.imageEditModelOptions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(message, imageEditModelOptions);
	}

}
