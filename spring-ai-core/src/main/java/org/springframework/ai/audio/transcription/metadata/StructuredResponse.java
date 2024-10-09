package org.springframework.ai.audio.transcription.metadata;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Piotr Olaszewski
 */
public record StructuredResponse(String language, Float duration, String text, @Nullable List<Word> words,
		@Nullable List<Segment> segments) {

	/**
	 * Extracted word and it's corresponding timestamps
	 *
	 * @param word The text content of the word.
	 * @param start The start time of the word in seconds.
	 * @param end The end time of the word in seconds.
	 */
	public record Word(String word, Float start, Float end) {
	}

	/**
	 * Segment of the transcribed text and its corresponding details.
	 *
	 * @param id Unique identifier of the segment.
	 * @param seek Seek offset of the segment.
	 * @param start Start time of the segment in seconds.
	 * @param end End time of the segment in seconds.
	 * @param text The text content of the segment.
	 * @param tokens Array of token IDs for the text content.
	 * @param temperature Temperature parameter used for generating the segment.
	 * @param avgLogprob Average logprob of the segment. If the value is lower than * -1,
	 * consider the logprobs failed.
	 * @param compressionRatio Compression ratio of the segment. If the value is greater
	 * than 2.4, consider the compression failed.
	 * @param noSpeechProb Probability of no speech in the segment. If the value is higher
	 * than 1.0 and the avg_logprob is below -1, consider this segment silent.
	 */
	public record Segment(Integer id, Integer seek, Float start, Float end, String text, List<Integer> tokens,
			Float temperature, Float avgLogprob, Float compressionRatio, Float noSpeechProb) {
	}
}
