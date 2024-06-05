package org.springframework.ai.chat.client;

import org.springframework.context.annotation.Description;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
@Description("")
public @interface Tool {

	@AliasFor(annotation = Description.class, attribute = "value")
	String description();

}
