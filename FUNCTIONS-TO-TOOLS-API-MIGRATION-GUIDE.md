# Migrating from FunctionCallback to ToolCallback API

This guide helps you migrate from the deprecated FunctionCallback API to the new ToolCallback API in Spring AI.

## Overview of Changes

The Spring AI project is moving from "functions" to "tools" terminology to better align with industry standards. This involves several API changes while maintaining backward compatibility through deprecated methods.

## Key Changes

1. `FunctionCallback` → `ToolCallback`
2. `FunctionCallback.builder().functions()` → `FunctionToolCallback.builder()`
3. `FunctionCallback.builder().method()` → `MethodToolCallback.builder()`
4. `FunctionCallingOptions` → `ToolCallingChatOptions`
5. Method names from `functions()` → `tools()`

## Migration Examples

### 1. Basic Function Callback

Before:
```java
FunctionCallback.builder()
    .function("getCurrentWeather", new MockWeatherService())
    .description("Get the weather in location")
    .inputType(MockWeatherService.Request.class)
    .build()
```

After:
```java
FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
    .description("Get the weather in location")
    .inputType(MockWeatherService.Request.class)
    .build()
```

### 2. ChatClient Usage

Before:
```java
String response = ChatClient.create(chatModel)
    .prompt()
    .user("What's the weather like in San Francisco?")
    .functions(FunctionCallback.builder()
        .function("getCurrentWeather", new MockWeatherService())
        .description("Get the weather in location")
        .inputType(MockWeatherService.Request.class)
        .build())
    .call()
    .content();
```

After:
```java
String response = ChatClient.create(chatModel)
    .prompt()
    .user("What's the weather like in San Francisco?")
    .tools(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
        .description("Get the weather in location")
        .inputType(MockWeatherService.Request.class)
        .build())
    .call()
    .content();
```

### 3. Method-Based Function Callbacks

Before:
```java
FunctionCallback.builder()
    .method("getWeatherInLocation", String.class, Unit.class)
    .description("Get the weather in location")
    .targetClass(TestFunctionClass.class)
    .build()
```

After:
```java
var toolMethod = ReflectionUtils.findMethod(
    TestFunctionClass.class, "getWeatherInLocation", String.class, Unit.class);

MethodToolCallback.builder()
    .toolDefinition(ToolDefinition.builder(toolMethod)
        .description("Get the weather in location")
        .build())
    .toolMethod(toolMethod)
    .build()
```

And you can use the same `ChatClient#tools()` API to register method-based tool callbackes:

```java
String response = ChatClient.create(chatModel)
    .prompt()
    .user("What's the weather like in San Francisco?")
    .tools(MethodToolCallback.builder()
        .toolDefinition(ToolDefinition.builder(toolMethod)
            .description("Get the weather in location")
            .build())
        .toolMethod(toolMethod)
        .build())
    .call()
    .content();
```

### 4. Options Configuration

Before:
```java
FunctionCallingOptions.builder()
    .model(modelName)
    .function("weatherFunction")
    .build()
```

After:
```java
ToolCallingChatOptions.builder()
    .model(modelName)
    .tools("weatherFunction")
    .build()
```

### 5. Default Functions in ChatClient Builder

Before:
```java
ChatClient.builder(chatModel)
    .defaultFunctions(FunctionCallback.builder()
        .function("getCurrentWeather", new MockWeatherService())
        .description("Get the weather in location")
        .inputType(MockWeatherService.Request.class)
        .build())
    .build()
```

After:
```java
ChatClient.builder(chatModel)
    .defaultTools(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
        .description("Get the weather in location")
        .inputType(MockWeatherService.Request.class)
        .build())
    .build()
```

### 6. Spring Bean Configuration

Before:
```java
@Bean
public FunctionCallback weatherFunctionInfo() {
    return FunctionCallback.builder()
        .function("WeatherInfo", new MockWeatherService())
        .description("Get the current weather")
        .inputType(MockWeatherService.Request.class)
        .build();
}
```

After:
```java
@Bean
public ToolCallback weatherFunctionInfo() {
    return FunctionToolCallback.builder("WeatherInfo", new MockWeatherService())
        .description("Get the current weather")
        .inputType(MockWeatherService.Request.class)
        .build();
}
```

## Breaking Changes

1. The `method()` configuration in function callbacks has been replaced with a more explicit method tool configuration using `ToolDefinition` and `MethodToolCallback`.

2. When using method-based callbacks, you now need to explicitly find the method using `ReflectionUtils` and provide it to the builder.

3. For non-static methods, you must now provide both the method and the target object:
```java
MethodToolCallback.builder()
    .toolDefinition(ToolDefinition.builder(toolMethod)
        .description("Description")
        .build())
    .toolMethod(toolMethod)
    .toolObject(targetObject)
    .build()
```

## Deprecated Methods

The following methods are deprecated and will be removed in a future release:

- `ChatClient.Builder.defaultFunctions(String...)`
- `ChatClient.Builder.defaultFunctions(FunctionCallback...)`
- `ChatClient.RequestSpec.functions()`

Use their `tools` counterparts instead.

## @Tool tool definition path. 

Now you can use the method-level annothation (`@Tool`) to register tools with Spring AI

```java 
public class Home {

    @Tool(description = "Turn light On or Off in a room.")
    public void turnLight(String roomName, boolean on) {
        // ...
        logger.info("Turn light in room: {} to: {}", roomName, on);
    }
}

Home homeAutomation = new HomeAutomation();

String response = ChatClient.create(this.chatModel).prompt()
        .user("Turn the light in the living room On.")
        .tools(homeAutomation)
        .call()
        .content();

```


## Additional Notes

1. The new API provides better separation between tool definition and implementation.
2. Tool definitions can be reused across different implementations.
3. The builder pattern has been simplified for common use cases.
4. Better support for method-based tools with improved error handling.

## Timeline

The deprecated methods will be maintained for backward compatibility in the current major version but will be removed in the next major release. It's recommended to migrate to the new API as soon as possible.
