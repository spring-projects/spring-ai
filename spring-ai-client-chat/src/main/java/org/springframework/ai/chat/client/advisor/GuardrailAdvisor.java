/*
 * The {@code GuardrailAdvisor} class is an implementation of both {@link CallAdvisor} and {@link StreamAdvisor}
 * that provides flexible input and output validation for chat client requests and responses.
 *
 * This advisor allows you to define custom validation logic for both user input and model output
 * by supplying {@link Predicate} functions. If the input or output does not pass the specified validation,
 * a configurable failure response is returned instead of proceeding with the normal processing chain.
 *
 * Typical use cases include enforcing content policies, blocking sensitive or inappropriate content,
 * or implementing custom guardrails for AI-powered chat applications.
 *
 * The class also provides a builder for convenient and readable instantiation.
 *
 * Example usage:
 * <pre>
 * GuardrailAdvisor advisor = new GuardrailAdvisor.Builder()
 *     .inputValidator(input -> !input.contains("forbidden"))
 *     .outputValidator(output -> !output.contains("restricted"))
 *     .failureResponse("Your request cannot be processed due to policy restrictions.")
 *     .order(1)
 *     .build();
 * </pre>
 *
 * @author Karson To
 * @since 1.0.0
 */

package org.springframework.ai.chat.client.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class GuardrailAdvisor implements CallAdvisor, StreamAdvisor {
    
    private static final String DEFAULT_FAILURE_RESPONSE =
            "Sorry, your request cannot be processed because it contains content that does not comply with our policy. "
                    + "Please revise your input and try again.";
    
    private static final int DEFAULT_ORDER = 0;
    
    private final String failureResponse;
    
    private final Predicate<String> inputValidator;
    
    private final Predicate<String> outputValidator;
    
    private final int order;
    
    
    public GuardrailAdvisor(Predicate<String> inputValidator, Predicate<String> outputValidator, String failureResponse,
            int order) {
        Assert.notNull(inputValidator, "Input validator must not be null!");
        Assert.notNull(outputValidator, "Output validator must not be null!");
        Assert.notNull(failureResponse, "Failure response must not be null!");
        this.inputValidator = inputValidator;
        this.outputValidator = outputValidator;
        this.failureResponse = failureResponse;
        this.order = order;
    }
    
    private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest) {
        return ChatClientResponse.builder().chatResponse(
                ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
                        .build()).context(Map.copyOf(chatClientRequest.context())).build();
    }
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String input = chatClientRequest.prompt().getContents();
        if (!inputValidator.test(input)) {
            return createFailureResponse(chatClientRequest);
        }
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        String output = null;
        if (response != null && response.chatResponse() != null && response.chatResponse().getResults() != null
                && !response.chatResponse().getResults().isEmpty()) {
            Generation generation = response.chatResponse().getResults().get(0);
            if (generation != null && generation.getOutput() != null) {
                output = generation.getOutput().getText();
            }
        }
        if (!outputValidator.test(output != null ? output : "")) {
            return createFailureResponse(chatClientRequest);
        }
        return response;
    }
    
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        String input = chatClientRequest.prompt().getContents();
        if (!inputValidator.test(input)) {
            return Flux.just(createFailureResponse(chatClientRequest));
        }
        return streamAdvisorChain.nextStream(chatClientRequest).map(response -> {
            String output = null;
            if (response != null && response.chatResponse() != null && response.chatResponse().getResults() != null
                    && !response.chatResponse().getResults().isEmpty()) {
                Generation generation = response.chatResponse().getResults().get(0);
                if (generation != null && generation.getOutput() != null) {
                    output = generation.getOutput().getText();
                }
            }
            if (!outputValidator.test(output != null ? output : "")) {
                return createFailureResponse(chatClientRequest);
            }
            return response;
        });
    }
    
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
    
    @Override
    public int getOrder() {
        return this.order;
    }
    
    public static final class Builder {
        
        private Predicate<String> inputValidator = s -> true;
        
        private Predicate<String> outputValidator = s -> true;
        
        private String failureResponse = DEFAULT_FAILURE_RESPONSE;
        
        private int order = DEFAULT_ORDER;
        
        private Builder() {
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public Builder inputValidator(Predicate<String> inputValidator) {
            this.inputValidator = inputValidator;
            return this;
        }
        
        public Builder outputValidator(Predicate<String> outputValidator) {
            this.outputValidator = outputValidator;
            return this;
        }
        
        public Builder failureResponse(String failureResponse) {
            this.failureResponse = failureResponse;
            return this;
        }
        
        public Builder order(int order) {
            this.order = order;
            return this;
        }
        
        public GuardrailAdvisor build() {
            return new GuardrailAdvisor(this.inputValidator, this.outputValidator, this.failureResponse, this.order);
        }
    }
}
