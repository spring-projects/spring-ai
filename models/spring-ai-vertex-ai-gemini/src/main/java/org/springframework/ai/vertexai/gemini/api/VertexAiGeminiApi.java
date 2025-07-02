package org.springframework.ai.vertexai.gemini.api;

import java.util.List;

public class VertexAiGeminiApi {

	public record LogProbs(Double avgLogprobs, List<TopContent> topCandidates,
			List<LogProbs.Content> chosenCandidates) {
		public record Content(String token, Float logprob, Integer id) {
		}

		public record TopContent(List<Content> candidates) {
		}
	}

}
