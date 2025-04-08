package org.springframework.ai.tool.autoconfigure;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.autoconfigure.annotation.EnableToolCallbackAutoRegistration;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link ImportBeanDefinitionRegistrar} implementation that registers a
 * {@link ToolAnnotatedBeanProcessor} bean based on the metadata from
 * {@link EnableToolCallbackAutoRegistration}.
 *
 * <p>
 * This registrar extracts package scanning information from the annotation attributes and
 * registers a {@link ToolAnnotatedBeanProcessor} to process beans containing
 * {@code @Tool}-annotated methods.
 *
 * @see EnableToolCallbackAutoRegistration
 * @see ToolAnnotatedBeanProcessor
 */
@ConditionalOnClass({ Tool.class, ToolCallbackProvider.class })
public class ToolCallbackAutoRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
			BeanNameGenerator importBeanNameGenerator) {
		Map<String, Object> attributes = importingClassMetadata
			.getAnnotationAttributes(EnableToolCallbackAutoRegistration.class.getName());

		if (attributes == null) {
			return;
		}

		Set<String> basePackages = getBasePackages(attributes);

		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(ToolAnnotatedBeanProcessor.class);
		beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

		ConstructorArgumentValues args = new ConstructorArgumentValues();
		args.addGenericArgumentValue(basePackages);
		beanDefinition.setConstructorArgumentValues(args);

		registry.registerBeanDefinition("ToolAnnotatedBeanProcessor", beanDefinition);
	}

	/**
	 * Extracts the base packages to scan from the
	 * {@code @EnableToolCallbackAutoRegistration} annotation attributes.
	 *
	 * <p>
	 * Supports the following attributes:
	 * <ul>
	 * <li>{@code value} - Shorthand for base packages</li>
	 * <li>{@code basePackages} - Explicit list of packages</li>
	 * <li>{@code basePackageClasses} - Infers packages from class types</li>
	 * </ul>
	 * @param attributes the annotation attributes
	 * @return a set of base package names to scan
	 */
	private Set<String> getBasePackages(@NonNull Map<String, Object> attributes) {
		Set<String> basePackages = new HashSet<>();

		Object[] valuePackages = (Object[]) attributes.get("value");
		if (valuePackages == null)
			valuePackages = new Object[0];

		for (Object obj : valuePackages) {
			if (obj instanceof String str && !str.isEmpty()) {
				basePackages.add(str);
			}
		}

		Object[] basePackagesAttr = (Object[]) attributes.get("basePackages");
		if (basePackagesAttr == null)
			basePackagesAttr = new Object[0];

		for (Object obj : basePackagesAttr) {
			if (obj instanceof String str && !str.isEmpty()) {
				basePackages.add(str);
			}
		}

		Object[] basePackageClasses = (Object[]) attributes.get("basePackageClasses");
		if (basePackageClasses == null)
			basePackageClasses = new Object[0];

		for (Object obj : basePackageClasses) {
			if (obj instanceof Class<?>) {
				// type casting instead of
				// if (obj instanceof Class<?> clazz)
				// for kotlin classes
				Class<?> clazz = (Class<?>) obj;
				basePackages.add(clazz.getPackage().getName());
			}
		}

		return basePackages;
	}

}
