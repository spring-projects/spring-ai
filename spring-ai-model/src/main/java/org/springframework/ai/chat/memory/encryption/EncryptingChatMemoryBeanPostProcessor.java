package org.springframework.ai.chat.memory.encryption;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Uses a configured {@link TextEncryptor text encryptor} to encrypt values before writes,
 * and decode those values from the read operations.
 *
 * @author Josh Long
 */
public class EncryptingChatMemoryBeanPostProcessor implements BeanPostProcessor {

	private final TextEncryptor encryptor;

	public EncryptingChatMemoryBeanPostProcessor(TextEncryptor encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {

		if (bean instanceof ChatMemoryRepository cmr && !(cmr instanceof EncryptingChatMemoryRepository)) {
			return new EncryptingChatMemoryRepository(cmr, encryptor);
		}
		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}

}
