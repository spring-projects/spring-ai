package org.springframework.ai.chat.call;

import org.springframework.ai.chat.messages.UserMessage;

import java.util.Map;

public interface ChatCallOperations {

	// Execute Methods for user message

	/**
	 * Execute a call to the AI model given a map of key-value pairs to replace in user
	 * text
	 * @param userMap The map that replaces placeholders in the user text. Only the first
	 * vararg entry will be used if present.
	 * @return The String result of calling the AI Model
	 */
	String execute(Map<String, Object>... userMap);

	/**
	 * Execute a call to the AI model given a String fo user text and a map of key-value
	 * pairs to replace in user text.
	 * @param userText The user text to use
	 * @param userMap An optional map that replaces placeholders in the user text. Only
	 * the * first vararg entry will be used if present
	 * @return The String result of calling the AI Model
	 */
	String execute(String userText, Map<String, Object>... userMap);

	/**
	 * Execute a call to the AI model given a UserMessage and a map of key-value pairs to
	 * replace in user text. Note, that this overloaded method handles the case of passing
	 * a List of Media objects in addition to user text
	 * @param userMessage The user text to use
	 * @param userMap An optional map that replaces placeholders in the user text. Only
	 * the first vararg entry will be used if present
	 * @return The String result of calling the AI Model
	 */
	String execute(UserMessage userMessage, Map<String, Object>... userMap);

	// Execute Methods for user and system messages

	String execute(Map<String, Object> userMap, Map<String, Object> systemMap);

	String execute(String userText, Map<String, Object> userMap, String systemText, Map<String, Object> systemMap); // could
	// make
	// systemMap
	// varargs

	String execute(UserMessage userMessage, Map<String, Object> userMap, String systemText,
			Map<String, Object> systemMap); // could make systemMap varargs

	// Execute Methods for user text that return a POJO

	<T> T execute(Class<T> returnType, Map<String, Object>... userMap);

	<T> T execute(Class<T> returnType, String userText, Map<String, Object>... userMap);

	<T> T execute(Class<T> returnType, UserMessage userMessage, Map<String, Object>... userMap);

	// Execute Methods for user and messages that return a POJO

	<T> T execute(Class<T> returnType, Map<String, Object> userMap, Map<String, Object> systemMap);

	<T> T execute(Class<T> returnType, String userText, Map<String, Object> userMap, String systemText,
			Map<String, Object> systemMap); // could make systemMap varargs

	<T> T execute(Class<T> returnType, UserMessage userMessage, Map<String, Object> userMap, String systemText,
			Map<String, Object> systemMap); // could make systemMap varargs

}
