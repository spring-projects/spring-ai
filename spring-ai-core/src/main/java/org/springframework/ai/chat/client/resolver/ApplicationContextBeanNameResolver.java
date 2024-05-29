package org.springframework.ai.chat.client.resolver;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

public class ApplicationContextBeanNameResolver implements BeanNameResolver {

	private final GenericApplicationContext context;

	public ApplicationContextBeanNameResolver(GenericApplicationContext context) {
		this.context = context;
	}

	@Override
	public <T> String resolveName(Class<T> beanType) {
		String[] namesForType = context.getBeanNamesForType(beanType);
		Assert.isTrue(namesForType.length == 1, "A bean must have a unique definiton");

		/*
		 * The following snippet could be used in other places to resolve the description
		 * from a function registered as a bean BeanDefinition beanDefinition =
		 * context.getBeanDefinition(namesForType[0]); String description =
		 * beanDefinition.getDescription();
		 */

		return namesForType[0];
	}

}
