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
package org.springframework.ai.google.genai.api;

import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.ai.google.genai.api.dto.GeminiContent;
import org.springframework.ai.google.genai.api.dto.GeminiImageRequest;
import org.springframework.ai.google.genai.api.dto.GeminiImageResponse;
import org.springframework.util.MimeType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Low-level adapter around {@link com.google.genai.Client} that knows how to:
 * <ul>
 * <li>map our internal Gemini* DTOs to the official Gemini Java SDK types,</li>
 * <li>call {@code client.models.generateContent(...)} for Nano Banana image models,</li>
 * <li>map {@link GenerateContentResponse} back into {@link GeminiImageResponse} DTO.</li>
 * <p>
 * This class is intentionally thin: all Spring AI-facing logic stays in
 * {@code GoogleGenAiImageModel}, while this class only encapsulates the actual SDK calls.
 *
 * @author Danil Temnikov
 */

public class GoogleGenAiImageApi {

	private final Client client;

	/**
	 * Create a new API wrapper around an existing Gemini {@link Client} instance.
	 * <p>
	 * The client should already be configured with API key, region, etc.
	 * @param client configured {@link Client} from the official Gemini Java SDK
	 */
	public GoogleGenAiImageApi(Client client) {
		this.client = client;
	}

	public GeminiImageResponse generateContent(String model, GeminiImageRequest request) {
		// Build GenerateContentConfig from our generationConfig (if present).
		GenerateContentConfig config = buildConfig(request.getGenerationConfig());

		// Map our DTO "contents" to SDK Content/Part types.
		Content sdkContent = toSdkContent(request.getContents());

		// Invoke Gemini Nano Banana image API through the official client.
		GenerateContentResponse sdkResponse = this.client.models.generateContent(model, sdkContent, config);

		// Map SDK response back to our DTO.
		return fromSdkResponse(sdkResponse);
	}

	/**
	 * Build {@link GenerateContentConfig} from our
	 * {@link GeminiImageRequest.GeminiGenerationConfig}.
	 * <p>
	 * Right now Nano Banana Java examples only show {@code responseModalities} for
	 * images. If/when the Java SDK exposes image-specific config (aspect ratio,
	 * resolution) at this level, the mapping can be extended here.
	 */
	private GenerateContentConfig buildConfig(@Nullable GeminiImageRequest.GeminiGenerationConfig generationConfig) {
		GenerateContentConfig.Builder builder = GenerateContentConfig.builder();

		if (generationConfig == null) {
			return builder.build();
		}

		List<String> modalities = generationConfig.getResponseModalities();
		if (modalities != null && !modalities.isEmpty()) {
			builder.responseModalities(modalities.toArray(new String[0]));
		}

		GeminiImageRequest.GeminiImageConfig imgCfg = generationConfig.getImageConfig();
		if (imgCfg == null) {
			return builder.build();
		}

		String aspectRatio = imgCfg.getAspectRatio();
		String imageSize = imgCfg.getImageSize();
		boolean hasAspect = aspectRatio != null && !aspectRatio.isBlank();
		boolean hasSize = imageSize != null && !imageSize.isBlank();

		if (!hasAspect && !hasSize) {
			return builder.build();
		}

		com.google.genai.types.ImageConfig.Builder imgBuilder = com.google.genai.types.ImageConfig.builder();

		if (hasAspect) {
			imgBuilder.aspectRatio(aspectRatio);
		}
		if (hasSize) {
			imgBuilder.imageSize(imageSize);
		}

		builder.imageConfig(imgBuilder.build());
		return builder.build();
	}

	/**
	 * Convert our {@link GeminiContent} list into a single SDK {@link Content}.
	 * <p>
	 * For Nano Banana usage patterns in the docs, we effectively send a single "user
	 * message" with one or more parts (text + images). So we:
	 *
	 * <ul>
	 * <li>flatten all parts from all {@code GeminiContent} entries into one list,</li>
	 * <li>create {@code Content.fromParts(...)} for the SDK.</li>
	 * </ul>
	 * <p>
	 * If your higher layer ever wants multiple distinct messages (with roles), this
	 * method is the right place to extend that mapping.
	 */
	private Content toSdkContent(List<GeminiContent> contents) {
		List<Part> sdkParts = new ArrayList<>();

		if (contents == null) {
			return Content.fromParts();
		}

		for (GeminiContent content : contents) {
			if (content == null || content.getParts() == null) {
				continue;
			}
			for (GeminiContent.GeminiPart part : content.getParts()) {
				if (part instanceof GeminiContent.GeminiPartText textPart) {
					String text = textPart.getText();
					if (text == null || text.isBlank()) {
						continue;
					}
					sdkParts.add(Part.fromText(text));
				} else if (part instanceof GeminiContent.GeminiPartInlineData inlinePart) {
					GeminiContent.GeminiPartInlineData.InlineData inlineData = inlinePart.getInlineData();
					if (inlineData == null) {
						continue;
					}

					MimeType mimeType = inlineData.getMimeType();
					String base64 = inlineData.getData();
					if (base64 == null || base64.isBlank()) {
						continue;
					}

					String mimeTypeStr = String.format("%s/%s", mimeType.getType(), mimeType.getSubtype());
					// According to types reference, Blob.data() is base64.
					Blob blob = Blob.builder().data(Base64.getDecoder().decode(base64)).mimeType(mimeTypeStr).build();
					sdkParts.add(Part.builder().inlineData(blob).build());
				}
				// Other GeminiPart specializations can be mapped here if you add them
				// later.
			}
		}

		// Defensive: if for some reason we ended up with no parts, create an empty
		// content.
		if (sdkParts.isEmpty()) {
			return Content.fromParts();
		}

		return Content.fromParts(sdkParts.toArray(new Part[0]));
	}

	/**
	 * Map {@link GenerateContentResponse} from the SDK back into our DTO structure.
	 * <p>
	 * We normalize the response into a single {@link GeminiContent} with a list of
	 * {@link GeminiContent.GeminiPart}:
	 *
	 * <ul>
	 * <li>Each SDK {@link Part} with text -> {@link GeminiContent.GeminiPartText}</li>
	 * <li>Each SDK {@link Part} with inlineData ->
	 * {@link GeminiContent.GeminiPartInlineData}</li>
	 * </ul>
	 * <p>
	 * If you later want to expose candidates / safety ratings / etc., this is the right
	 * place to enrich {@link GeminiImageResponse}.
	 */
	private GeminiImageResponse fromSdkResponse(GenerateContentResponse sdkResponse) {
		List<GeminiContent.GeminiPart> ourParts = new ArrayList<>();

		if (sdkResponse == null || sdkResponse.parts() == null) {
			GeminiContent content = new GeminiContent(ourParts);
			return new GeminiImageResponse(Collections.singletonList(new GeminiImageResponse.GeminiCandidate(content)));
		}

		for (Part part : sdkResponse.parts()) {
			part.text().ifPresent(text -> {
				if (text == null || text.isBlank()) {
					return;
				}
				ourParts.add(new GeminiContent.GeminiPartText(text));
			});

			part.inlineData().ifPresent(blob -> {
				String mimeType = blob.mimeType().orElse(null);

				byte[] bytes = blob.data().orElse(null);
				if (bytes == null || bytes.length == 0) {
					return;
				}
				String base64Data = Base64.getEncoder().encodeToString(bytes);
				GeminiContent.GeminiPartInlineData.InlineData inlineData = new GeminiContent.GeminiPartInlineData.InlineData(
						mimeType, base64Data);
				ourParts.add(new GeminiContent.GeminiPartInlineData(inlineData));
			});
		}

		GeminiContent content = new GeminiContent(ourParts);
		return new GeminiImageResponse(Collections.singletonList(new GeminiImageResponse.GeminiCandidate(content)));
	}

}
