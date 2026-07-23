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

package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GuardrailAdvisor}.
 * <p>
 * This test class verifies the input and output validation logic of the GuardrailAdvisor, ensuring that inappropriate
 * content is properly blocked and a failure response is returned.
 * <p>
 * Main test coverage includes:
 * <ul>
 *   <li>Blocking requests when the input does not meet policy requirements, and ensuring the downstream chain is not called.</li>
 *   <li>Blocking responses when the output does not meet policy requirements, and returning a failure message.</li>
 *   <li>Allowing requests and responses to pass through when both input and output are valid.</li>
 *   <li>Validating the same logic for both synchronous (call) and asynchronous (stream) advisor chains.</li>
 * </ul>
 * <p>
 * All dependencies are mocked using Mockito, and both normal and streaming scenarios are covered.
 *
 * @author Karson To
 */

class GuardrailAdvisorTests {
    
    @Test
    void testInputBlocked() {
        Predicate<String> inputValidator = input -> !input.contains("block");
        Predicate<String> outputValidator = output -> true;
        GuardrailAdvisor advisor = GuardrailAdvisor.Builder.builder().inputValidator(inputValidator)
                .outputValidator(outputValidator).order(0).build();
        
        ChatClientRequest request = mockRequest("this should block");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        
        ChatClientResponse response = advisor.adviseCall(request, chain);
        
        assertTrue(response.chatResponse().getResults().get(0).getOutput().getText().contains("cannot be processed"));
        verify(chain, never()).nextCall(any());
    }
    
    @Test
    void testOutputBlocked() {
        Predicate<String> inputValidator = input -> true;
        Predicate<String> outputValidator = output -> !output.contains("badword");
        GuardrailAdvisor advisor = GuardrailAdvisor.Builder.builder().inputValidator(inputValidator)
                .outputValidator(outputValidator).order(0).build();
        
        ChatClientRequest request = mockRequest("normal input");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        
        // 模拟返回带有 badword 的响应
        AssistantMessage msg = new AssistantMessage("this contains badword");
        Generation gen = new Generation(msg);
        ChatResponse chatResponse = new ChatResponse(List.of(gen));
        ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).context(Map.of())
                .build();
        when(chain.nextCall(any())).thenReturn(clientResponse);
        
        ChatClientResponse response = advisor.adviseCall(request, chain);
        
        assertTrue(response.chatResponse().getResults().get(0).getOutput().getText().contains("cannot be processed"));
    }
    
    @Test
    void testPassThrough() {
        Predicate<String> inputValidator = input -> true;
        Predicate<String> outputValidator = output -> true;
        GuardrailAdvisor advisor = GuardrailAdvisor.Builder.builder().inputValidator(inputValidator)
                .outputValidator(outputValidator).order(0).build();
        
        ChatClientRequest request = mockRequest("hello");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        
        AssistantMessage msg = new AssistantMessage("all good");
        Generation gen = new Generation(msg);
        ChatResponse chatResponse = new ChatResponse(List.of(gen));
        ChatClientResponse clientResponse = ChatClientResponse.builder().chatResponse(chatResponse).context(Map.of())
                .build();
        when(chain.nextCall(any())).thenReturn(clientResponse);
        
        ChatClientResponse response = advisor.adviseCall(request, chain);
        
        assertEquals("all good", response.chatResponse().getResults().get(0).getOutput().getText());
    }
    
    @Test
    void testStreamInputBlocked() {
        Predicate<String> inputValidator = input -> input.length() < 5;
        Predicate<String> outputValidator = output -> true;
        GuardrailAdvisor advisor = GuardrailAdvisor.Builder.builder().inputValidator(inputValidator)
                .outputValidator(outputValidator).order(0).build();
        
        ChatClientRequest request = mockRequest("toolonginput");
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        
        Flux<ChatClientResponse> flux = advisor.adviseStream(request, chain);
        List<ChatClientResponse> responses = flux.collectList().block();
        
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertTrue(responses.get(0).chatResponse().getResults().get(0).getOutput().getText()
                .contains("cannot be processed"));
        verify(chain, never()).nextStream(any());
    }
    
    @Test
    void testStreamOutputBlocked() {
        Predicate<String> inputValidator = input -> true;
        Predicate<String> outputValidator = output -> !output.contains("bad");
        GuardrailAdvisor advisor = GuardrailAdvisor.Builder.builder().inputValidator(inputValidator)
                .outputValidator(outputValidator).order(0).build();
        
        ChatClientRequest request = mockRequest("ok");
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        
        AssistantMessage msg1 = new AssistantMessage("good");
        AssistantMessage msg2 = new AssistantMessage("bad output");
        Generation gen1 = new Generation(msg1);
        Generation gen2 = new Generation(msg2);
        ChatResponse chatResponse1 = new ChatResponse(List.of(gen1));
        ChatResponse chatResponse2 = new ChatResponse(List.of(gen2));
        ChatClientResponse resp1 = ChatClientResponse.builder().chatResponse(chatResponse1).context(Map.of()).build();
        ChatClientResponse resp2 = ChatClientResponse.builder().chatResponse(chatResponse2).context(Map.of()).build();
        
        when(chain.nextStream(any())).thenReturn(Flux.just(resp1, resp2));
        
        List<ChatClientResponse> responses = advisor.adviseStream(request, chain).collectList().block();
        
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("good", responses.get(0).chatResponse().getResults().get(0).getOutput().getText());
        assertTrue(responses.get(1).chatResponse().getResults().get(0).getOutput().getText()
                .contains("cannot be processed"));
    }
    
    private ChatClientRequest mockRequest(String content) {
        ChatClientRequest request = mock(ChatClientRequest.class);
        Prompt prompt = new Prompt(new UserMessage(content));
        when(request.prompt()).thenReturn(prompt);
        when(request.context()).thenReturn(Map.of());
        return request;
    }
}