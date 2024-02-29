package org.springframework.ai.watsonx.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.ai.chat.messages.*;

import java.util.List;

public class MessageToPromptConverterTest {

	private MessageToPromptConverter converter;

	@Before
	public void setUp() {
		converter = MessageToPromptConverter.create().withHumanPrompt("").withAssistantPrompt("");
	}

	@Test
	public void testSingleUserMessage() {
		Message userMessage = new UserMessage("User message");
		String expected = "User message";
		Assert.assertEquals(expected, converter.messageToString(userMessage));
	}

	@Test
	public void testSingleAssistantMessage() {
		Message assistantMessage = new AssistantMessage("Assistant message");
		String expected = "Assistant message";
		Assert.assertEquals(expected, converter.messageToString(assistantMessage));
	}

	@Disabled
	public void testFunctionMessageType() {
		Message functionMessage = new FunctionMessage("Function message");
		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
			converter.messageToString(functionMessage);
		});
	}

	@Test
	public void testSystemMessageType() {
		Message systemMessage = new SystemMessage("System message");
		String expected = "System message";
		Assert.assertEquals(expected, converter.messageToString(systemMessage));
	}

	@Test
	public void testCustomHumanPrompt() {
		converter.withHumanPrompt("Custom Human: ");
		Message userMessage = new UserMessage("User message");
		String expected = "Custom Human: User message";
		Assert.assertEquals(expected, converter.messageToString(userMessage));
	}

	@Test
	public void testCustomAssistantPrompt() {
		converter.withAssistantPrompt("Custom Assistant: ");
		Message assistantMessage = new AssistantMessage("Assistant message");
		String expected = "Custom Assistant: Assistant message";
		Assert.assertEquals(expected, converter.messageToString(assistantMessage));
	}

	@Test
	public void testEmptyMessageList() {
		String expected = "";
		Assert.assertEquals(expected, converter.toPrompt(List.of()));
	}

	@Test
	public void testSystemMessageList() {
		String msg = "this is a LLM prompt";
		SystemMessage message = new SystemMessage(msg);
		Assert.assertEquals(msg, converter.toPrompt(List.of(message)));
	}

	@Test
	public void testUserMessageList() {
		List<Message> messages = List.of(new UserMessage("User message"));
		String expected = "User message";
		Assert.assertEquals(expected, converter.toPrompt(messages));
	}

	@Disabled
	public void testUnsupportedMessageType() {
		List<Message> messages = List.of(new FunctionMessage("Unsupported message"));
		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
			converter.toPrompt(messages);
		});
	}

}
