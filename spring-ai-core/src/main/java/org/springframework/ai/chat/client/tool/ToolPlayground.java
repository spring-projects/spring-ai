package org.springframework.ai.chat.client.tool;

import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Function;

public class ToolPlayground {

    record Request(String name) {
    }

    record Response(String title) {
    }

    static class ToolBean implements Function<Request, Response> {

        @Override
        public Response apply(Request request) {
            return null;
        }
    }

    /*
     * TODO: It appears that we could make this work with the existing implementation by
     * changing the tool methods to return a FunctionCallWrapper.
     * I think it might be worth renaming the FunctionCallbackWrapper
     */
    public void playground(ChatClient client) {

        client.prompt()
                .function("Name", "description", (Request request) -> new Response(""));

        /*
         * This seems like a pretty good interface for using the name based API
         */
        client.prompt()
                .function(Tools.getByName("somefunction"));

        /*
         * This seems like a pretty good interface for using the bean based API
         */
        client.prompt()
                .function(Tools.getByBean(ToolBean.class));

        /*
         * To get proper type inference we need to add the generics here. If we do not add
         * this then we do not have type information on the input or the output To me this
         * looks kinda ugly and makes the caller write less elegant code
         */
        client.prompt()
                .function(
                        Tools.<Request, Response>getByLambda(
                                "somefunction",
                                "description",
                                request -> new Response("")
                        ));
        /*
         * To a limited extent it is possible to address this ugly syntax by defining an
         * explicit type on the input type. And it is fair to notice that this issue also
         * exists with the current implementation
         */
        client.prompt()
                .function(
                        Tools.getByLambda(
                                "somefunction",
                                "description",
                                (Request reqest) -> new Response("")
                        ));
    }

}
