package org.springframework.ai.azure.openai.metadata;

import org.springframework.ai.image.ImageGenerationMetadata;

import java.util.Objects;

public class AzureOpenAiImageGenerationMetadata implements ImageGenerationMetadata {

    private final String revisedPrompt;

    public AzureOpenAiImageGenerationMetadata(String revisedPrompt) {
        this.revisedPrompt = revisedPrompt;
    }

    public String getRevisedPrompt() {
        return revisedPrompt;
    }

    public String toString() {
        return "AzureOpenAiImageGenerationMetadata{" + "revisedPrompt='" + revisedPrompt + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AzureOpenAiImageGenerationMetadata that))
            return false;
        return Objects.equals(revisedPrompt, that.revisedPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revisedPrompt);
    }

}
