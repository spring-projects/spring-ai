package org.springframework.ai.openai.client;

import org.springframework.ai.model.AbstractToolFunctionCallback;

import java.util.function.Function;

public class GenericFunctionCallback extends AbstractToolFunctionCallback<Object, Object> {

    private Function function;

    protected GenericFunctionCallback(String name, String description, Class inputType, Function function) {
        super(name, description, inputType);
        this.function = function;
    }

    @Override
    public Object apply(Object o) {
        return function.apply(o);
    }
}
