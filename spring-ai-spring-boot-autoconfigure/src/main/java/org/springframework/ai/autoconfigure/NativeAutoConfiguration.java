package org.springframework.ai.autoconfigure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * exists only to bring in the generic resource hints common to everything
 */
@Configuration
@ImportRuntimeHints(NativeHints.class)
class NativeAutoConfiguration {

}
