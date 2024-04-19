package org.springframework.ai.chat.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

public class ChatCall implements ChatCallOperations {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ChatClient chatClient;

	private final Optional<String> userString;

	private final List<Media> mediaList;

	private final Map<String, Object> userMap;

	private final Optional<String> systemString;

	private final Map<String, Object> systemMap;

	private final Optional<ChatOptions> chatOptions;

	public ChatCall(ChatClient chatClient, Optional<String> userString, Map<String, Object> userMap, List<Media> media,
			Optional<String> systemString, Map<String, Object> systemMap, Optional<ChatOptions> chatOptions) {
		Objects.requireNonNull(chatClient, "ChatClient cannot be null.");
		this.chatClient = chatClient;
		this.userString = userString;
		this.userMap = userMap != null ? Collections.unmodifiableMap(new HashMap<>(userMap))
				: Collections.unmodifiableMap(new HashMap<>());
		this.mediaList = media != null ? media : new ArrayList<>();
		this.systemString = systemString;
		this.systemMap = systemMap != null ? Collections.unmodifiableMap(new HashMap<>(systemMap))
				: Collections.unmodifiableMap(new HashMap<>());
		this.chatOptions = chatOptions;
	}

	public static ChatCallBuilder builder(ChatClient chatClient) {
		return new ChatCallBuilder(chatClient);
	}

	@Override
	public String execute(Map<String, Object>... userMap) {
		if (userMap.length == 0) {
			return execute(Collections.emptyMap(), Collections.emptyMap());
		}
		else {
			return execute(userMap[0], new HashMap<>());
		}
	}

	@Override
	public String execute(String userText, Map<String, Object>... userMap) {
		if (userMap.length == 0) {
			return execute(userText, Collections.emptyMap(), "", Collections.emptyMap());
		}
		else {
			return execute(userText, userMap[0], "", Collections.emptyMap());
		}
	}

	@Override
	public String execute(UserMessage userMessage, Map<String, Object>... userMap) {
		if (userMap.length == 0) {
			return execute(userMessage, Collections.emptyMap(), "", Collections.emptyMap());
		}
		else {
			return execute(userMessage, userMap[0], "", Collections.emptyMap());
		}
	}

	// Execute Methods for user and system messages

	@Override
	public String execute(Map<String, Object> runtimeUserMap, Map<String, Object> runtimeSystemMap) {
		return execute("", runtimeUserMap, "", runtimeSystemMap);
	}

	@Override
	public String execute(String userText, Map<String, Object> runtimeUserMap, String systemText,
			Map<String, Object> runtimeSystemMap) {
		List<Message> messageList = new ArrayList<>();
		doCreateUserMessage(userText, this.mediaList, runtimeUserMap, messageList);
		doCreateSystemMessage(systemText, runtimeSystemMap, messageList);
		Prompt prompt = doCreatePrompt(messageList);
		ChatResponse chatResponse = doExecute(prompt);
		String response = doCreateStringResponse(chatResponse);
		return response;
	}

	@Override
	public String execute(UserMessage userMessage, Map<String, Object> runtimeUserMap, String systemText,
			Map<String, Object> runtimeSystemMap) {
		List<Message> messageList = new ArrayList<>();

		doCreateUserMessage(userMessage.getContent(), userMessage.getMedia(), runtimeUserMap, messageList);
		doCreateSystemMessage(systemText, runtimeSystemMap, messageList);
		Prompt prompt = doCreatePrompt(messageList);
		ChatResponse chatResponse = doExecute(prompt);
		String response = doCreateStringResponse(chatResponse);
		return response;
	}

	// Execute Methods for user message that return a POJO

	@Override
	public <T> T execute(Class<T> returnType, Map<String, Object>... runtimeUserMap) {
		var userMessage = new UserMessage(this.userString.get());
		if (runtimeUserMap.length == 0) {
			return execute(returnType, userMessage, Collections.emptyMap(), "", Collections.emptyMap());
		}
		else {
			return execute(returnType, userMessage, runtimeUserMap[0], "", Collections.emptyMap());
		}
	}

	@Override
	public <T> T execute(Class<T> returnType, String userText, Map<String, Object>... runtimeUserMap) {
		var userMessage = new UserMessage(userText);
		if (runtimeUserMap.length == 0) {
			return execute(returnType, userMessage, Collections.emptyMap(), "", Collections.emptyMap());
		}
		else {
			return execute(returnType, userMessage, runtimeUserMap[0], "", Collections.emptyMap());
		}
	}

	@Override
	public <T> T execute(Class<T> returnType, UserMessage userMessage, Map<String, Object>... runtimeUserMap) {
		if (runtimeUserMap.length == 0) {
			return execute(returnType, userMessage, Collections.emptyMap(), "", Collections.emptyMap());
		}
		else {
			return execute(returnType, userMessage, runtimeUserMap[0], "", Collections.emptyMap());
		}
	}

	// Execute Methods for user and system message that return a POJO

	@Override
	public <T> T execute(Class<T> returnType, Map<String, Object> runtimeUserMap,
			Map<String, Object> runtimeSystemMap) {
		return execute(returnType, new UserMessage(this.userString.get()), runtimeUserMap, "", runtimeSystemMap);
	}

	@Override
	public <T> T execute(Class<T> returnType, String userText, Map<String, Object> runtimeUserMap, String systemText,
			Map<String, Object> runtimeSystemMap) {
		return execute(returnType, new UserMessage(this.userString.get()), runtimeUserMap, "", runtimeSystemMap);
	}

	@Override
	public <T> T execute(Class<T> returnType, UserMessage userMessage, Map<String, Object> runtimeUserMap,
			String systemText, Map<String, Object> runtimeSystemMap) {
		List<Message> messageList = new ArrayList<>();
		String userTextToUse = userMessage.getContent() + System.lineSeparator() + "{format}";
		var parser = new BeanOutputParser<>(returnType);
		runtimeUserMap.put("format", parser.getFormat());
		doCreateUserMessage(userTextToUse, userMessage.getMedia(), runtimeUserMap, messageList);
		doCreateSystemMessage(systemText, runtimeSystemMap, messageList);

		Prompt prompt = doCreatePrompt(messageList);
		ChatResponse chatResponse = doExecute(prompt);
		String stringResponse = doCreateStringResponse(chatResponse);
		T parsedResponse = parser.parse(stringResponse);
		return parsedResponse;
	}

	protected void doCreateUserMessage(String userText, List<Media> runtimeMedia, Map<String, Object> runtimeUserMap,
			List<Message> messageList) {
		PromptTemplate userPromptTemplate = null;

		if (StringUtils.hasText(userText)) {
			userPromptTemplate = new PromptTemplate(userText);
		}
		else if (this.userString.isPresent()) {
			userPromptTemplate = new PromptTemplate(this.userString.get());
		}

		List<Media> mediaListToUse;
		if (CollectionUtils.isEmpty(runtimeMedia)) {
			mediaListToUse = this.mediaList;
		}
		else {
			mediaListToUse = runtimeMedia;
		}

		if (userPromptTemplate != null) {
			Map userMapToUse = new HashMap(this.userMap);
			userMapToUse.putAll(runtimeUserMap);
			messageList.add(userPromptTemplate.createMessage(userMapToUse, mediaListToUse));
		}
		else {
			logger.warn("No user message set.");
		}
	}

	protected void doCreateSystemMessage(String systemText, Map<String, Object> runtimeSystemMap,
			List<Message> messageList) {
		SystemPromptTemplate systemPromptTemplate = null;
		if (StringUtils.hasText(systemText)) {
			systemPromptTemplate = new SystemPromptTemplate(systemText);
		}
		else if (this.systemString.isPresent()) {
			systemPromptTemplate = new SystemPromptTemplate(this.systemString.get());
		}
		if (systemPromptTemplate != null) {
			Map systemMapToUse = new HashMap(this.systemMap);
			systemMapToUse.putAll(runtimeSystemMap);
			messageList.add(systemPromptTemplate.createMessage(systemMapToUse));
		}
		else {
			logger.trace("No system message set");
		}
	}

	protected Prompt doCreatePrompt(List<Message> messageList) {
		Prompt prompt;
		if (this.chatOptions.isPresent()) {
			prompt = new Prompt(messageList, this.chatOptions.get());
		}
		else {
			prompt = new Prompt(messageList);
		}
		logger.debug("Created Prompt: {}", prompt);
		return prompt;
	}

	protected ChatResponse doExecute(Prompt prompt) {
		ChatResponse chatResponse = this.chatClient.call(prompt);
		return chatResponse;
	}

	protected static String doCreateStringResponse(ChatResponse chatResponse) {
		return chatResponse.getResult().getOutput().getContent();
	}

	public static class ChatCallBuilder {

		private final ChatClient chatClient;

		private Optional<String> userString = Optional.empty();

		private Map<String, Object> userMap = Collections.emptyMap();

		private List<Media> mediaList = Collections.emptyList();

		private Optional<String> systemString = Optional.empty();

		private Map<String, Object> systemMap = Collections.emptyMap();

		private Optional<ChatOptions> chatOptions = Optional.empty();

		private ChatCallBuilder(ChatClient chatClient) {
			Objects.requireNonNull(chatClient, "ChatClient cannot be null.");
			this.chatClient = chatClient;
		}

		public ChatCallBuilder withSystemString(String systemString) {
			this.systemString = Optional.ofNullable(systemString);
			return this;
		}

		public ChatCallBuilder withSystemMap(Map<String, Object> systemMap) {
			this.systemMap = systemMap;
			return this;
		}

		public ChatCallBuilder withUserString(String userString) {
			this.userString = Optional.ofNullable(userString);
			return this;
		}

		public ChatCallBuilder withUserMap(Map<String, Object> userMap) {
			this.userMap = userMap;
			return this;
		}

		public ChatCallBuilder withMedia(List<Media> media) {
			this.mediaList = media;
			return this;
		}

		public ChatCallBuilder withChatOptions(ChatOptions chatOptions) {
			this.chatOptions = Optional.ofNullable(chatOptions);
			return this;
		}

		public ChatCall build() {
			return new ChatCall(this.chatClient, this.userString, this.userMap, this.mediaList, this.systemString,
					this.systemMap, this.chatOptions);
		}

	}

}