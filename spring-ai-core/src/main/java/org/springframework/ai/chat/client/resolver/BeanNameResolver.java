package org.springframework.ai.chat.client.resolver;

public interface BeanNameResolver {

	<T> String resolveName(Class<T> beanType);

}
