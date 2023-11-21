/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.client;

import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import io.reactivex.Flowable;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;

import java.util.List;

public class OpenAiStreamClient extends OpenAiClient {
    private final OpenAiService openAiService;

    public OpenAiStreamClient(OpenAiService openAiService) {
        super(openAiService);
        this.openAiService = openAiService;
    }

    public Flowable<ChatCompletionChunk> generateStream(Prompt prompt) {

        List<Message> messages = prompt.getMessages();

        List<ChatMessage> theoMessages =
                messages.stream().map(message -> new ChatMessage(message.getMessageTypeValue(), message.getContent()))
                        .toList();

        ChatCompletionRequest chatCompletionRequest =
                ChatCompletionRequest.builder().model(getModel()).temperature(getTemperature()).messages(theoMessages)
                        .stream(true).build();

        return this.openAiService.streamChatCompletion(chatCompletionRequest);
    }

}
