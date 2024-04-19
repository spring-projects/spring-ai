package org.springframework.ai.chat.transformer;

import org.springframework.ai.chat.transformer.PromptContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.transformer.PromptTransformer;
import org.springframework.ai.document.Document;
import org.springframework.ai.node.Node;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transforms the Prompt by taking to the current prompt in the Prompt Context and adding
 * additional context to create a new prompt. The default user text contains the
 * placeholder names "question" and "context". The "question" placeholder is filled using
 * the value of the current UserMessage and the "context" placeholder is filled with
 * Documents contained in the PromptContext's Nodes.
 */
public class QuestionContextAugmentor implements PromptTransformer {

	private static final String DEFAULT_USER_PROMPT_TEXT = """
			   "Context information is below.\\n"
			   "---------------------\\n"
			   "{context}\\n"
			   "---------------------\\n"
			   "Given the context information and not prior knowledge, "
			   "answer the question. If the answer is not in the context, inform "
			   "the user that you can't answer the question.\\n"
			   "Question: {question}\\n"
			   "Answer: "
			""";

	@Override
	public PromptContext transform(PromptContext promptContext) {
		String context = doCreateContext(promptContext.getNodes());
		Map<String, Object> contextMap = doCreateContextMap(promptContext.getPrompt(), context);
		Prompt prompt = doCreatePrompt(promptContext.getPrompt(), contextMap);
		promptContext.setPrompt(prompt);
		promptContext.addPromptHistory(prompt);
		// For now return the modified instance instead of a copy
		return promptContext;
	}

	protected String doCreateContext(List<Node<?>> data) {
		return data.stream()
			.filter(node -> node instanceof Document)
			.map(node -> (Document) node)
			.map(Node::getContent)
			.collect(Collectors.joining(System.lineSeparator()));
	}

	private Map<String, Object> doCreateContextMap(Prompt prompt, String context) {
		String originalUserMessage = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		return Map.of("context", context, "question", originalUserMessage);
	}

	protected Prompt doCreatePrompt(Prompt originalPrompt, Map<String, Object> contextMap) {
		PromptTemplate promptTemplate = new PromptTemplate(DEFAULT_USER_PROMPT_TEXT);
		Message userMessageToAppend = promptTemplate.createMessage(contextMap);
		List<Message> messageList = originalPrompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() != MessageType.USER)
			.collect(Collectors.toList());
		messageList.add(userMessageToAppend);
		return new Prompt(messageList, (ChatOptions) originalPrompt.getOptions());
	}

}
