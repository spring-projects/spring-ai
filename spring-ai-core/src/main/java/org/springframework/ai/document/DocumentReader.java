package org.springframework.ai.document;

import java.util.List;
import java.util.function.Supplier;

public interface DocumentReader extends Supplier<List<Document>> {

}
