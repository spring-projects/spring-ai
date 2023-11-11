package org.springframework.experimental.ai.model;

import java.util.List;

public record TitanTextGenerationConfig(double temperature, Double topP, double maxTokenCount, List<String> stopToken) {
}
