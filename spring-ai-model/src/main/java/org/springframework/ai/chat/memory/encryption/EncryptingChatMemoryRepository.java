package org.springframework.ai.chat.memory.encryption;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * Wraps {@link ChatMemoryRepository a ChatMemoryRepository}, encrypting and decrypting
 * reads and writes respectively using a Spring Security {@link TextEncryptor text
 * encryptor}.
 *
 * @author Josh Long
 */
public class EncryptingChatMemoryRepository implements ChatMemoryRepository {

	private final ChatMemoryRepository target;

	private final TextEncryptor textEncryptor;

	public EncryptingChatMemoryRepository(ChatMemoryRepository target, TextEncryptor textEncryptor) {
		this.target = target;
		this.textEncryptor = textEncryptor;
	}

	private Message transform(Message message, Function<String, String> function) {

		var transformedText = function.apply(message.getText());

		// todo is there a case to be made that we should seal the message hierarchy?
		if (message instanceof SystemMessage systemMessage) {
			return systemMessage.mutate().text(transformedText).build();
		}

		if (message instanceof UserMessage userMessage) {
			return userMessage.mutate().text(transformedText).build();
		}

		if (message instanceof AssistantMessage assistantMessage) {
			return assistantMessage.mutate().text(transformedText).build();
		}

		return message;
	}

	private Message decrypt(Message message) {
		return this.transform(message, this.textEncryptor::decrypt);
	}

	private Message encrypt(Message message) {
		return this.transform(message, this.textEncryptor::encrypt);
	}

	@NotNull
	@Override
	public List<String> findConversationIds() {
		return this.target.findConversationIds();
	}

	@NotNull
	@Override
	public List<Message> findByConversationId(@NotNull String conversationId) {
		return this.target.findByConversationId(conversationId).stream().map(this::decrypt).toList();
	}

	@Override
	public void saveAll(@NotNull String conversationId, List<Message> messages) {
		this.target.saveAll(conversationId, messages.stream().map(this::encrypt).collect(Collectors.toList()));
	}

	@Override
	public void deleteByConversationId(@NotNull String conversationId) {
		this.target.deleteByConversationId(conversationId);
	}

}
