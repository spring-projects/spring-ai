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

package org.springframework.ai.image;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.model.MutableResponseMetadata;

/**
 * Represents metadata associated with an image response. It provides additional
 * information about the generative response from an AI model, including the creation
 * timestamp of the generated image.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ImageResponseMetadata extends MutableResponseMetadata {

	private final Long created;

	private Usage usage;

	public ImageResponseMetadata() {
		this(System.currentTimeMillis(), new EmptyUsage());
	}

	public ImageResponseMetadata(Long created, Usage usage) {
		this.created = created;
		this.usage = usage;
	}

	public Long getCreated() {
		return this.created;
	}

	public Usage getUsage() {
		return this.usage;
	}

	public void setUsage(Usage usage) {
		this.usage = usage;
	}

}
