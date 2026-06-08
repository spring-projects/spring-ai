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

package org.springframework.ai.image.observation;

import java.util.StringJoiner;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.CollectionUtils;

/**
 * Handler for emitting image prompt content to logs.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ImageModelPromptContentObservationHandler implements ObservationHandler<ImageModelObservationContext> {

	private static final Log logger = LogFactory.getLog(ImageModelPromptContentObservationHandler.class);

	@Override
	public void onStop(ImageModelObservationContext context) {
		if (!CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
			StringJoiner promptMessagesJoiner = new StringJoiner(", ", "[", "]");
			context.getRequest()
				.getInstructions()
				.forEach(message -> promptMessagesJoiner.add("\"" + message.getText() + "\""));

			if (logger.isInfoEnabled()) {
				logger.info("Image Model Prompt Content:\n" + promptMessagesJoiner);
			}
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ImageModelObservationContext;
	}

}
