package org.springframework.ai.bedrock.titan;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse.CompletionReason;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse.Result;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;

/**
 * @author Jihoon Kim
 * @since 1.0.0 M4
 */
@ExtendWith(MockitoExtension.class)
public class BedrockTitanChatModelTests {

	@Mock
	TitanChatBedrockApi chatApi = new TitanChatBedrockApi(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), ModelOptionsUtils.OBJECT_MAPPER,
			Duration.ofMinutes(2));

	@Test
	public void call_test() {
		given(chatApi.getModelId()).willReturn(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id());
		given(chatApi.chatCompletion(any())).willReturn(new TitanChatResponse(4, List
			.of(new Result(3, "this is joke", null), new Result(4, "see you next time", CompletionReason.FINISH))));
		BedrockTitanChatModel chatModel = new BedrockTitanChatModel(chatApi);
		ChatResponse response = chatModel.call(new Prompt("Tell me a joke", BedrockTitanChatOptions.builder().build()));

		Usage usage = response.getMetadata().getUsage();

		assert null != response.getMetadata();
		assert usage.getPromptTokens() == 4;
		assert usage.getGenerationTokens() == 7;
	}

}
