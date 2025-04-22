package org.springframework.ai.qwen.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.qwen.QwenChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenApiIT {

	private static final Logger logger = LoggerFactory.getLogger(QwenApiIT.class);

	private QwenApi qwenApi() {
		return QwenApi.builder().apiKey(System.getenv("DASHSCOPE_API_KEY")).build();
	}

	private List<Message> history() {
		SystemMessage systemMessage = new SystemMessage("""
				Your name is Jack.
				You like to answer other people's questions briefly.
				It's rainy today.
				""");

		UserMessage query1 = new UserMessage("Hello. What's your name?");
		AssistantMessage answer = new AssistantMessage("Jack!");
		UserMessage query2 = new UserMessage("How about the weather today?");

		return List.of(systemMessage, query1, answer, query2);
	}

	@Test
	public void callNonMultimodalModel() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_MAX.getName()).build();
		Prompt prompt = new Prompt(history(), options);

		QwenApi api = qwenApi();

		ChatResponse response = api.call(prompt, null);
		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("rain");
	}

	@Test
	public void streamingCallNonMultimodalModel() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_MAX.getName()).build();
		Prompt prompt = new Prompt(history(), options);

		QwenApi api = qwenApi();

		// @formatter:off
        String generationTextFromStream = api.streamCall(prompt, null)
                .collectList()
                .block()
                .stream()
                .map(ChatResponse::getResults)
                .flatMap(List::stream)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        // @formatter:on

		logger.info(generationTextFromStream);
		assertThat(generationTextFromStream).containsIgnoringCase("rain");
	}

	@Test
	public void callMultimodalModel() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_VL_MAX.getName()).build();
		Resource resource = new ClassPathResource("multimodal.test.png");
		UserMessage message = new UserMessage("Explain what do you see on this picture?",
				Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(resource).build());
		Prompt prompt = new Prompt(message, options);

		QwenApi api = qwenApi();

		ChatResponse response = api.call(prompt, null);
		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@Test
	public void callNonMultimodalModelWithCustomizedParameter() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_MAX.getName()).build();
		Prompt prompt = new Prompt(history(), options);

		QwenApi api = qwenApi();
		api.setGenerationParamCustomizer(builder -> builder.stopString("rain"));

		ChatResponse response = api.call(prompt, null);
		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).doesNotContainIgnoringCase("rain");
	}

	@Test
	public void streamingCallNonMultimodalModelWithCustomizedParameter() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_MAX.getName()).build();
		Prompt prompt = new Prompt(history(), options);

		QwenApi api = qwenApi();
		api.setGenerationParamCustomizer(builder -> builder.stopString("rain"));

		// @formatter:off
        String generationTextFromStream = api.streamCall(prompt, null)
                .collectList()
                .block()
                .stream()
                .map(ChatResponse::getResults)
                .flatMap(List::stream)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        // @formatter:on

		logger.info(generationTextFromStream);
		assertThat(generationTextFromStream).doesNotContainIgnoringCase("rain");
	}

	@Test
	public void callMultimodalModelWithCustomizedParameter() {
		QwenChatOptions options = QwenChatOptions.builder().model(QwenModel.QWEN_VL_MAX.getName()).build();
		Resource resource = new ClassPathResource("multimodal.test.png");
		UserMessage message = new UserMessage("Explain what do you see on this picture?",
				Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(resource).build());
		Prompt prompt = new Prompt(message, options);

		QwenApi api = qwenApi();
		api.setMultimodalConversationParamCustomizer(MockImageContentFilter::handle);

		ChatResponse response = api.call(prompt, null);
		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).doesNotContainIgnoringCase("bananas", "apple", "bowl",
				"basket", "fruit stand");
	}

}
