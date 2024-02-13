package org.springframework.ai.aot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AiRuntimeHintsTests {

	@JsonInclude
	static class TestApi {

		static class FooBar {

		}

		record Foo(@JsonProperty("name") String name) {
		}

		@JsonInclude
		enum Bar {

			A, B

		}

	}

	@Test
	void discoverRelevantClasses() throws Exception {
		var classes = AiRuntimeHints.findJsonAnnotatedClassesInPackage(TestApi.class);
		var included = Set.of(TestApi.Bar.class, TestApi.Foo.class)
			.stream()
			.map(t -> TypeReference.of(t.getName()))
			.collect(Collectors.toSet());
		LogFactory.getLog(getClass()).info(classes);
		Assert.state(classes.containsAll(included), "there should be all of the enumerated classes. ");
	}

}
