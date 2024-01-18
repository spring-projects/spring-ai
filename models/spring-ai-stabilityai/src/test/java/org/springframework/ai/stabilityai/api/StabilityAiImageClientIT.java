package org.springframework.ai.stabilityai.api;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiImageClientIT {

	StabilityAiApi stabilityAiApi = new StabilityAiApi(System.getenv("STABILITYAI_API_KEY"));

}
