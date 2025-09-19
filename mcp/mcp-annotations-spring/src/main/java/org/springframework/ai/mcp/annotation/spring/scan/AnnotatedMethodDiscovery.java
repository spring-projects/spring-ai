package org.springframework.ai.mcp.annotation.spring.scan;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

class AnnotatedMethodDiscovery {

	protected final Set<Class<? extends Annotation>> targetAnnotations;

	AnnotatedMethodDiscovery(Set<Class<? extends Annotation>> targetAnnotations) {
		this.targetAnnotations = targetAnnotations;
	}

	protected Set<Class<? extends Annotation>> scan(Class<?> beanClass) {
		Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();

		// Scan all methods in the bean class
		ReflectionUtils.doWithMethods(beanClass, method -> {
			this.targetAnnotations.forEach(annotationType -> {
				if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
					foundAnnotations.add(annotationType);
				}
			});
		});
		return foundAnnotations;
	}

}
