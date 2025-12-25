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

package org.springframework.ai.moderation;

import org.jspecify.annotations.Nullable;

/**
 * A builder class for creating instances of ModerationOptions. Use the builder() method
 * to obtain a new instance of ModerationOptionsBuilder. Use the withModel() method to set
 * the model for moderation. Use the build() method to build the ModerationOptions
 * instance.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public final class ModerationOptionsBuilder {

	private final ModerationModelOptionsImpl options = new ModerationModelOptionsImpl();

	private ModerationOptionsBuilder() {

	}

	public static ModerationOptionsBuilder builder() {
		return new ModerationOptionsBuilder();
	}

	public ModerationOptionsBuilder model(String model) {
		this.options.setModel(model);
		return this;
	}

	public ModerationOptions build() {
		return this.options;
	}

	private class ModerationModelOptionsImpl implements ModerationOptions {

		private @Nullable String model;

		@Override
		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(String model) {
			this.model = model;
		}

	}

}
