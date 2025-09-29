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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.model.ModelRequest;

public class ImagePrompt implements ModelRequest<List<ImageMessage>> {

	private final List<ImageMessage> messages;

	private ImageOptions imageModelOptions;

	public ImagePrompt(List<ImageMessage> messages) {
		this.messages = messages;
	}

	public ImagePrompt(List<ImageMessage> messages, ImageOptions imageModelOptions) {
		this.messages = messages;
		this.imageModelOptions = imageModelOptions;
	}

	public ImagePrompt(ImageMessage imageMessage, ImageOptions imageOptions) {
		this(Collections.singletonList(imageMessage), imageOptions);
	}

	public ImagePrompt(String instructions, ImageOptions imageOptions) {
		this(new ImageMessage(instructions), imageOptions);
	}

	public ImagePrompt(String instructions) {
		this(new ImageMessage(instructions), ImageOptionsBuilder.builder().build());
	}

	@Override
	public List<ImageMessage> getInstructions() {
		return this.messages;
	}

	@Override
	public ImageOptions getOptions() {
		return this.imageModelOptions;
	}

	@Override
	public String toString() {
		return "NewImagePrompt{" + "messages=" + this.messages + ", imageModelOptions=" + this.imageModelOptions + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ImagePrompt that)) {
			return false;
		}
		return Objects.equals(this.messages, that.messages)
				&& Objects.equals(this.imageModelOptions, that.imageModelOptions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.messages, this.imageModelOptions);
	}

}
