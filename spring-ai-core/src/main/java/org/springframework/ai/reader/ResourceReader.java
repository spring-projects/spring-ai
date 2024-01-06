package org.springframework.ai.reader;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.function.Function;

/**
 * Used to read a resource and convert it into a list of {@link Document}s.
 */
public interface ResourceReader extends Function<Resource, List<Document>> {

}
