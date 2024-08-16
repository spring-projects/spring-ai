/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.image.observation;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for image model exchanges.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ImageModelObservationContext extends ModelObservationContext<ImagePrompt, ImageResponse> {

	private final ImageOptions requestOptions;

	ImageModelObservationContext(ImagePrompt imagePrompt, String provider, ImageOptions requestOptions) {
		super(imagePrompt,
				AiOperationMetadata.builder().operationType(AiOperationType.IMAGE.value()).provider(provider).build());
		Assert.notNull(requestOptions, "requestOptions cannot be null");
		this.requestOptions = requestOptions;
	}

	public ImageOptions getRequestOptions() {
		return requestOptions;
	}

	public String getOperationType() {
		return AiOperationType.IMAGE.value();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ImagePrompt imagePrompt;

		private String provider;

		private ImageOptions requestOptions;

		private Builder() {
		}

		public Builder imagePrompt(ImagePrompt imagePrompt) {
			this.imagePrompt = imagePrompt;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public Builder requestOptions(ImageOptions requestOptions) {
			this.requestOptions = requestOptions;
			return this;
		}

		public ImageModelObservationContext build() {
			return new ImageModelObservationContext(imagePrompt, provider, requestOptions);
		}

	}

}
