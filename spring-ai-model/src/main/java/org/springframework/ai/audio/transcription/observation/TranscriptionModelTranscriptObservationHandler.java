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

package org.springframework.ai.audio.transcription.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.util.StringUtils;

/**
 * Handler for emitting the transcript a transcription model returned to logs. The audio
 * given to the model is never read or logged: it may be a stream the model consumes once.
 *
 * @author Christian Tzolov
 * @since 2.0.2
 */
public class TranscriptionModelTranscriptObservationHandler
		implements ObservationHandler<TranscriptionModelObservationContext> {

	private static final Log logger = LogFactory.getLog(TranscriptionModelTranscriptObservationHandler.class);

	@Override
	public void onStop(TranscriptionModelObservationContext context) {
		AudioTranscriptionResponse response = context.getResponse();
		if (response == null) {
			return;
		}
		String transcript = response.getResult().getOutput();
		if (StringUtils.hasText(transcript) && logger.isInfoEnabled()) {
			logger.info("Transcription Model Transcript:\n" + transcript);
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof TranscriptionModelObservationContext;
	}

}
