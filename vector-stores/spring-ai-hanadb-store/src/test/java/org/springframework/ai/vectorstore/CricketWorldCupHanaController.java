/*
 * Copyright 2024 - 2025 the original author or authors.
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
package org.springframework.ai.vectorstore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiAudioSpeechClient;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@RestController
@Slf4j
public class CricketWorldCupHanaController {
    private final VectorStore hanaCloudVectorStore;
    private final ChatClient chatClient;
    private final OpenAiAudioSpeechClient openAiAudioSpeechClient;

    @Autowired
    public CricketWorldCupHanaController(ChatClient chatClient,
                                         VectorStore hanaCloudVectorStore,
                                         OpenAiAudioSpeechClient openAiAudioSpeechClient) {
        this.chatClient = chatClient;
        this.hanaCloudVectorStore = hanaCloudVectorStore;
        this.openAiAudioSpeechClient = openAiAudioSpeechClient;
    }

    @PostMapping("/ai/hana-vector-store/cricket-world-cup/purge-embeddings")
    public ResponseEntity<String> purgeEmbeddings() {
        int deleteCount = ((HanaCloudVectorStore) this.hanaCloudVectorStore).purgeEmbeddings();
        log.info("{} embeddings purged from CRICKET_WORLD_CUP table in Hana DB", deleteCount);
        return ResponseEntity.ok().body(String.format("%d embeddings purged from CRICKET_WORLD_CUP table in Hana DB", deleteCount));
    }

    @PostMapping("/ai/hana-vector-store/cricket-world-cup/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("pdf") MultipartFile file) throws IOException {
        Resource pdf = file.getResource();
        Supplier<List<Document>> reader = new PagePdfDocumentReader(pdf);
        Function<List<Document>, List<Document>> splitter = new TokenTextSplitter();
        List<Document> documents = splitter.apply(reader.get());
        log.info("{} documents created from pdf file: {}", documents.size(), pdf.getFilename());
        hanaCloudVectorStore.accept(documents);
        return ResponseEntity.ok().body(String.format("%d documents created from pdf file: %s",
                documents.size(), pdf.getFilename()));
    }

    @GetMapping("/ai/hana-vector-store/cricket-world-cup")
    public Map<String, String> hanaVectorStoreSearch(@RequestParam(value = "message") String message) {
        var documents = this.hanaCloudVectorStore.similaritySearch(message);
        var inlined = documents.stream().map(Document::getContent).collect(Collectors.joining(System.lineSeparator()));
        var similarDocsMessage = new SystemPromptTemplate("Based on the following: {documents}")
                .createMessage(Map.of("documents", inlined));

        var userMessage = new UserMessage(message);
        Prompt prompt = new Prompt(List.of(similarDocsMessage, userMessage));
        String generation = chatClient.call(prompt).getResult().getOutput().getContent();
        log.info("Generation: {}", generation);
        return Map.of("generation", generation);
    }

    @GetMapping("/ai/hana-vector-store/cricket-world-cup/text-to-speech")
    public ResponseEntity<byte[]> hanaVectorStoreSearchTextToSpeech(@RequestParam(value = "message") String message) {
        OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
                .withModel("tts-1")
                .withVoice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                .withResponseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .withSpeed(1.0f)
                .build();

        var documents = this.hanaCloudVectorStore.similaritySearch(SearchRequest.query(message).withTopK(5));
        var inlined = documents.stream().map(Document::getContent).collect(Collectors.joining(System.lineSeparator()));
        var similarDocsMessage = new SystemPromptTemplate("Based on the following: {documents}")
                .createMessage(Map.of("documents", inlined));

        var userMessage = new UserMessage(message);
        Prompt prompt = new Prompt(List.of(similarDocsMessage, userMessage));
        String textResponse = chatClient.call(prompt).getResult().getOutput().getContent();
        log.info("Generation: {}", textResponse);

        SpeechPrompt speechPrompt = new SpeechPrompt(textResponse, speechOptions);
        SpeechResponse response = openAiAudioSpeechClient.call(speechPrompt);
        byte[] audioBytes = response.getResult().getOutput();
        log.info("Speech response generated");
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("Accept-Ranges", "bytes")
                .body(Arrays.copyOfRange(audioBytes, 0, audioBytes.length));
    }
}


