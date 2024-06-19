/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.AudioTranscriptionFormat;
import com.azure.ai.openai.models.AudioTranscriptionOptions;
import com.azure.ai.openai.models.AudioTranscriptionTimestampGranularity;
import com.azure.core.http.rest.Response;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions.GranularityType;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions.StructuredResponse;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions.StructuredResponse.Segment;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions.StructuredResponse.Word;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions.TranscriptResponseFormat;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * AzureOpenAI audio transcription client implementation for backed by
 * {@link OpenAIClient}. You provide as input the audio file you want to transcribe and
 * the desired output file format of the transcription of the audio.
 *
 * @author Piotr Olaszewski
 */
public class AzureOpenAiAudioTranscriptionModel implements Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	private static final List<AudioTranscriptionFormat> JSON_FORMATS = List.of(AudioTranscriptionFormat.JSON,
			AudioTranscriptionFormat.VERBOSE_JSON);

	private static final String FILENAME_MARKER = "filename.wav";

	private final OpenAIClient openAIClient;

	private final AzureOpenAiAudioTranscriptionOptions defaultOptions;

	public AzureOpenAiAudioTranscriptionModel(OpenAIClient openAIClient, AzureOpenAiAudioTranscriptionOptions options) {
		this.openAIClient = openAIClient;
		this.defaultOptions = options;
	}

	public String call(Resource audioResource) {
		AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioResource);
		return call(transcriptionRequest).getResult().getOutput();
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt audioTranscriptionPrompt) {
		String deploymentOrModelName = getDeploymentName(audioTranscriptionPrompt);
		AudioTranscriptionOptions audioTranscriptionOptions = toAudioTranscriptionOptions(audioTranscriptionPrompt);

		AudioTranscriptionFormat responseFormat = audioTranscriptionOptions.getResponseFormat();
		if (JSON_FORMATS.contains(responseFormat)) {
			var audioTranscription = openAIClient.getAudioTranscription(deploymentOrModelName, FILENAME_MARKER,
					audioTranscriptionOptions);

			List<Word> words = null;
			if (audioTranscription.getWords() != null) {
				words = audioTranscription.getWords().stream().map(w -> {
					float start = (float) w.getStart().toSeconds();
					float end = (float) w.getEnd().toSeconds();
					return new Word(w.getWord(), start, end);
				}).toList();
			}

			List<Segment> segments = null;
			if (audioTranscription.getSegments() != null) {
				segments = audioTranscription.getSegments().stream().map(s -> {
					float start = (float) s.getStart().toSeconds();
					float end = (float) s.getEnd().toSeconds();
					return new Segment(s.getId(), s.getSeek(), start, end, s.getText(), s.getTokens(),
							(float) s.getTemperature(), (float) s.getAvgLogprob(), (float) s.getCompressionRatio(),
							(float) s.getNoSpeechProb());
				}).toList();
			}

			Float duration = audioTranscription.getDuration() == null ? null
					: (float) audioTranscription.getDuration().toSeconds();
			StructuredResponse structuredResponse = new StructuredResponse(audioTranscription.getLanguage(), duration,
					audioTranscription.getText(), words, segments);

			AudioTranscription transcript = new AudioTranscription(structuredResponse.text());
			AzureOpenAiAudioTranscriptionResponseMetadata metadata = AzureOpenAiAudioTranscriptionResponseMetadata
				.from(structuredResponse);

			return new AudioTranscriptionResponse(transcript, metadata);
		}
		else {
			Response<String> audioTranscription = openAIClient.getAudioTranscriptionTextWithResponse(
					deploymentOrModelName, FILENAME_MARKER, audioTranscriptionOptions, null);
			String text = audioTranscription.getValue();
			AudioTranscription transcript = new AudioTranscription(text);
			return new AudioTranscriptionResponse(transcript, AzureOpenAiAudioTranscriptionResponseMetadata.from(text));
		}
	}

	private String getDeploymentName(AudioTranscriptionPrompt audioTranscriptionPrompt) {
		var runtimeOptions = audioTranscriptionPrompt.getOptions();

		if (defaultOptions != null) {
			runtimeOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
					AzureOpenAiAudioTranscriptionOptions.class);
		}

		if (runtimeOptions instanceof AzureOpenAiAudioTranscriptionOptions azureOpenAiAudioTranscriptionOptions) {
			String deploymentName = azureOpenAiAudioTranscriptionOptions.getDeploymentName();
			if (StringUtils.hasText(deploymentName)) {
				return deploymentName;
			}
		}

		return runtimeOptions.getModel();
	}

	private AudioTranscriptionOptions toAudioTranscriptionOptions(AudioTranscriptionPrompt audioTranscriptionPrompt) {
		var runtimeOptions = audioTranscriptionPrompt.getOptions();

		if (this.defaultOptions != null) {
			runtimeOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
					AzureOpenAiAudioTranscriptionOptions.class);
		}

		byte[] bytes = toBytes(audioTranscriptionPrompt.getInstructions());
		AudioTranscriptionOptions audioTranscriptionOptions = new AudioTranscriptionOptions(bytes);

		if (runtimeOptions instanceof AzureOpenAiAudioTranscriptionOptions azureOpenAiAudioTranscriptionOptions) {
			String model = azureOpenAiAudioTranscriptionOptions.getModel();
			if (StringUtils.hasText(model)) {
				audioTranscriptionOptions.setModel(model);
			}

			String language = azureOpenAiAudioTranscriptionOptions.getLanguage();
			if (StringUtils.hasText(language)) {
				audioTranscriptionOptions.setLanguage(language);
			}

			String prompt = azureOpenAiAudioTranscriptionOptions.getPrompt();
			if (StringUtils.hasText(prompt)) {
				audioTranscriptionOptions.setPrompt(prompt);
			}

			Float temperature = azureOpenAiAudioTranscriptionOptions.getTemperature();
			if (temperature != null) {
				audioTranscriptionOptions.setTemperature(temperature.doubleValue());
			}

			TranscriptResponseFormat responseFormat = azureOpenAiAudioTranscriptionOptions.getResponseFormat();
			List<GranularityType> granularityType = azureOpenAiAudioTranscriptionOptions.getGranularityType();

			if (responseFormat != null) {
				audioTranscriptionOptions.setResponseFormat(responseFormat.getValue());
				if (responseFormat == TranscriptResponseFormat.VERBOSE_JSON && granularityType == null) {
					granularityType = List.of(GranularityType.SEGMENT);
				}
			}

			if (granularityType != null) {
				Assert.isTrue(responseFormat == TranscriptResponseFormat.VERBOSE_JSON,
						"response_format must be set to verbose_json to use timestamp granularities.");
				List<AudioTranscriptionTimestampGranularity> granularity = granularityType.stream()
					.map(GranularityType::getValue)
					.toList();
				audioTranscriptionOptions.setTimestampGranularities(granularity);
			}
		}

		return audioTranscriptionOptions;
	}

	private static byte[] toBytes(Resource resource) {
		try {
			return resource.getInputStream().readAllBytes();
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

}
