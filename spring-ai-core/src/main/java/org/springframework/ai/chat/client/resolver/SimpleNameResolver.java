package org.springframework.ai.chat.client.resolver;

public class SimpleNameResolver implements BeanNameResolver {

	@Override
	public <T> String resolveName(Class<T> beanType) {
		return beanType.getSimpleName().substring(0, 1).toLowerCase() + beanType.getSimpleName().substring(1);
	}

}
