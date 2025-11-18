/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openaiofficial.metadata;

import com.openai.models.images.ImagesResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.util.Assert;

import java.util.Objects;

/**
 * Represents the metadata for image response using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialImageResponseMetadata extends ImageResponseMetadata {

    private final Long created;

    /**
     * Creates a new OpenAiOfficialImageResponseMetadata.
     * @param created the creation timestamp
     */
    protected OpenAiOfficialImageResponseMetadata(Long created) {
        this.created = created;
    }

    /**
     * Creates metadata from an ImagesResponse.
     * @param imagesResponse the OpenAI images response
     * @return the metadata instance
     */
    public static OpenAiOfficialImageResponseMetadata from(ImagesResponse imagesResponse) {
        Assert.notNull(imagesResponse, "imagesResponse must not be null");
        return new OpenAiOfficialImageResponseMetadata(imagesResponse.created());
    }

    @Override
    public Long getCreated() {
        return this.created;
    }

    @Override
    public String toString() {
        return "OpenAiOfficialImageResponseMetadata{" + "created=" + created + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenAiOfficialImageResponseMetadata that)) {
            return false;
        }
        return Objects.equals(this.created, that.created);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.created);
    }

}
