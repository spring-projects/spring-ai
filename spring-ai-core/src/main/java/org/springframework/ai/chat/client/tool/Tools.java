package org.springframework.ai.chat.client.tool;

import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

import java.util.function.Function;

/**
 * TODO: In the current implementation the application context is required to define a tool by name and by type
 * At runtime this should not be an issue, however accessing a non-static attribute from static methods has obvious issues.
 *
 * Proposal:
 * I think it would make sense to redesign how functions / tools are handled in general. Functions created via the
 * FunctionCallbackWrappers do a lot of processing when they are defined in the ChatClient. However, the functions created
 * via the bean name are only handled much later (if my reading of the codebase is correct this happens during execution of the call to the LLM).
 * I believe it would be better to handle all functions / tools in a more uniform way, so either process them as they are defined
 * or when the LLM call is being executed. This would not only make the code more consistent but also make it easier to extend the
 * mechanisms by which functions / tools can be defined.
 */
public class Tools {

    private static GenericApplicationContext context;

    public Tools(GenericApplicationContext context) {
        Tools.context = context;
    }


    public static <Req, Res> FunctionCallbackWrapper<Object, Object> getByName(String name) {
        // TODO we need to get to the application context.
        // Get the bean by name and then create the tool itself...
		String description = context.getBeanDefinition(name).getDescription();
		return FunctionCallbackWrapper.builder(Function.identity())
                .withName(name)
                .withDescription(description)
                .build();
    }

    public static <Req, Res, T> FunctionCallbackWrapper<Object, Object> getByBean(Class<T> beanType) {
        String[] namesForType = context.getBeanNamesForType(beanType);
        Assert.isTrue(namesForType.length == 1, "A bean must have a unique definiton");
        String name = namesForType[0];
        String description = context.getBeanDefinition(name).getDescription();
        return FunctionCallbackWrapper.builder(Function.identity())
                .withName(name)
                .withDescription(description)
                .build();
    }

    public static <Req, Res> FunctionCallbackWrapper<Req, Res> getByLambda(String name, String description,
                                                                           Function<Req, Res> func) {
        return FunctionCallbackWrapper.builder(func).withDescription("description").withName(name).build();
    }

}
