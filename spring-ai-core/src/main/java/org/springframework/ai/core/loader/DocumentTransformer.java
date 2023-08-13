package org.springframework.ai.core.loader;

import java.util.List;
import java.util.function.Function;

public interface DocumentTransformer extends Function<List<Document>, List<Document>> {

}
