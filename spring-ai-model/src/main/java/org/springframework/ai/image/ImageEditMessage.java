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

import java.util.List;

import org.springframework.ai.content.Media;

public class ImageEditMessage {

	private List<Media> image;

	private String prompt;

	public ImageEditMessage(List<Media> image, String prompt) {
		this.image = image;
		this.prompt = prompt;
	}

	public List<Media> getImage() {
		return image;
	}

	public String getPrompt() {
		return prompt;
	}

}
