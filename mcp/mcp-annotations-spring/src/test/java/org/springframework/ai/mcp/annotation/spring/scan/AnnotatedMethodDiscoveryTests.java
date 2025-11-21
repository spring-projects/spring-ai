/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.annotation.spring.scan;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link AnnotatedMethodDiscovery}.
 *
 * @author lance
 */
class AnnotatedMethodDiscoveryTests {

	@Test
	void testScanAnnotationMethod() {
		Set<Class<? extends Annotation>> annotations = Set.of(MyAnnotation.class, AnotherAnnotation.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(annotations);
		Set<Class<? extends Annotation>> scanned = discovery.scan(PlainClass.class);

		assertThat(scanned).containsExactlyInAnyOrder(MyAnnotation.class, AnotherAnnotation.class);
	}

	@Test
	void testReturnEmpty() {
		Set<Class<? extends Annotation>> annotations = Set.of(MyAnnotation.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(annotations);
		Set<Class<? extends Annotation>> scanned = discovery.scan(Set.class);

		assertThat(scanned).isEmpty();
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface MyAnnotation {

	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface AnotherAnnotation {

	}

	static class PlainClass {

		@MyAnnotation
		public void methodA() {
		}

		@AnotherAnnotation
		public void methodB() {
		}

		public void methodC() {
		}

	}

}
