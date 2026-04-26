package code.chg.agent.infa.openai;

import code.chg.agent.llm.*;
import code.chg.agent.config.OpenAIConfig;
import code.chg.agent.utils.MessageIdGenerator;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title OpenAIClientFactory
 * @description Factory for creating OpenAI-backed LLM clients.
 */
public class OpenAIClientFactory implements LLMClientFactory {

    private static final OpenAIClient OPEN_AI_CLIENT = OpenAIOkHttpClient.builder()
            .baseUrl(OpenAIConfig.getBaseUrl())
            .apiKey(OpenAIConfig.getApiKey())
            .build();


    public LLMClient getClient() {
        return new OpenAILLMClient();
    }

    private static class OpenAILLMClient implements LLMClient {

        private final AtomicBoolean used = new AtomicBoolean(false);

        @Override
        public List<LLMMessage> chat(LLMRequest request) {
            try (code.chg.agent.llm.StreamResponse streamResponse = streamChat(request)) {
                while (streamResponse.hasNext()) {
                    streamResponse.next();
                }
                return streamResponse.completion();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public code.chg.agent.llm.StreamResponse streamChat(LLMRequest request) {
            if (!used.compareAndSet(false, true)) {
                throw new RuntimeException("OpenAILLMClient instance can only be used once for either chat or streamChat. Please create a new instance for each request.");
            }
            ChatCompletionCreateParams params = createOpenAIParams(request);
            String messageId = MessageIdGenerator.generateWithPrefix("ai");
            StreamResponse<ChatCompletionChunk> streamResponse = OPEN_AI_CLIENT.chat().completions().createStreaming(params);
            return new OpenAIStreamResponse(streamResponse, messageId);
        }

        @Override
        public <T> T structureResponseChat(String input, Class<T> responseType) {
            StructuredResponseCreateParams<T> params = ResponseCreateParams.builder()
                    .input(input)
                    .model(OpenAIConfig.getDefaultModel())
                    .text(responseType)
                    .build();
            return OPEN_AI_CLIENT.responses().create(params).output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst().orElse(null);
        }

        private static ChatCompletionCreateParams createOpenAIParams(LLMRequest request) {
            ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                    .model(OpenAIConfig.getDefaultModel())
                    .parallelToolCalls(OpenAIConfig.isParallelToolCalls())
                    .streamOptions(ChatCompletionStreamOptions.builder()
                            .includeUsage(true)
                            .build());

            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                for (LLMMessage message : request.getMessages()) {
                    builder.addMessage(convertToChatCompletionMessage(message));
                }
            }

            if (request.getTools() != null && !request.getTools().isEmpty()) {
                for (ToolDefinition toolDef : request.getTools()) {
                    builder.addTool(convertToolDefinition(toolDef));
                }
            }
            return builder.build();
        }

    }


    private static class OpenAIStreamResponse implements code.chg.agent.llm.StreamResponse {
        private final StreamResponse<ChatCompletionChunk> streamResponse;
        private final Iterator<ChatCompletionChunk> chunkIterator;
        private final ChatCompletionAccumulator chatCompletionAccumulator;
        private final String messageId;

        private OpenAIStreamResponse(StreamResponse<ChatCompletionChunk> streamResponse, String messageId) {
            this.streamResponse = streamResponse;
            this.chunkIterator = streamResponse.stream().iterator();
            this.chatCompletionAccumulator = ChatCompletionAccumulator.create();
            this.messageId = messageId;
        }

        @Override
        public List<LLMMessage> completion() {
            ChatCompletion chatCompletion = this.chatCompletionAccumulator.chatCompletion();
            return chatCompletion.choices().stream().map(item -> OpenAILLMMessage.from(item.message(), messageId)).toList();
        }

        @Override
        public TokenUsage usage() {
            ChatCompletion chatCompletion = this.chatCompletionAccumulator.chatCompletion();
            if (chatCompletion.usage().isEmpty()) {
                return null;
            }
            var usage = chatCompletion.usage().get();
            TokenUsage tokenUsage = new TokenUsage();
            tokenUsage.setPromptTokens(Math.toIntExact(usage.promptTokens()));
            tokenUsage.setCompletionTokens(Math.toIntExact(usage.completionTokens()));
            tokenUsage.setTotalTokens(Math.toIntExact(usage.totalTokens()));
            int cachedPromptTokens = usage.promptTokensDetails()
                    .flatMap(details -> details.cachedTokens().map(Math::toIntExact))
                    .orElse(0);
            tokenUsage.setCachedPromptTokens(cachedPromptTokens);
            return tokenUsage;
        }

        @Override
        public void close() {
            streamResponse.close();
        }

        @Override
        public boolean hasNext() {
            return chunkIterator.hasNext();
        }

        @Override
        public LLMMessageChunk next() {
            if (!this.hasNext()) {
                return null;
            }
            ChatCompletionChunk chunk = chunkIterator.next();
            this.chatCompletionAccumulator.accumulate(chunk);
            List<ChatCompletionChunk.Choice> choices = chunk.choices();
            OpenAILLMMessageChunk openAILLMMessageChunk = new OpenAILLMMessageChunk(messageId);
            if (choices.isEmpty()) {
                return openAILLMMessageChunk;
            }
            for (ChatCompletionChunk.Choice choice : choices) {
                ChatCompletionChunk.Choice.Delta messageChunk = choice.delta();
                openAILLMMessageChunk.mergeContent(messageChunk.content().orElse(null));

                // Extract reasoning/thinking content from additional properties (OpenAI o-series models)
                // _additionalProperties() returns Map<String, com.openai.core.JsonValue>
                java.util.Map<String, com.openai.core.JsonValue> additionalProps = messageChunk._additionalProperties();
                if (additionalProps.containsKey("reasoning_content")) {
                    com.openai.core.JsonValue reasoningValue = additionalProps.get("reasoning_content");
                    if (reasoningValue != null) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode reasoningNode =
                                    code.chg.agent.utils.JsonUtil.getObjectMapper().valueToTree(reasoningValue);
                            if (!reasoningNode.isNull()) {
                                openAILLMMessageChunk.mergeThinkingContent(reasoningNode.asText());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                Optional<List<ChatCompletionChunk.Choice.Delta.ToolCall>> toolCallChunksOpt = messageChunk.toolCalls();
                if (toolCallChunksOpt.isPresent()) {
                    List<ChatCompletionChunk.Choice.Delta.ToolCall> toolCallChunks = toolCallChunksOpt.get();
                    for (ChatCompletionChunk.Choice.Delta.ToolCall toolCallChunk : toolCallChunks) {
                        int index = Math.toIntExact(toolCallChunk.index());
                        String id = toolCallChunk.id().orElse(null);
                        String name = Objects.requireNonNull(toolCallChunk.function().map(ChatCompletionChunk.Choice.Delta.ToolCall.Function::name).orElse(null)).orElse(null);
                        String arguments = Objects.requireNonNull(toolCallChunk.function().map(ChatCompletionChunk.Choice.Delta.ToolCall.Function::arguments).orElse(null)).orElse(null);
                        openAILLMMessageChunk.mergeToolCallChunk(index, id, name, arguments);
                    }
                }
            }
            return openAILLMMessageChunk;
        }

    }

    private static class OpenAILLMMessage implements LLMMessage {

        private final ChatCompletionMessage chatCompletionMessage;
        private final String messageId;

        private OpenAILLMMessage(ChatCompletionMessage chatCompletionMessage, String messageId) {
            this.chatCompletionMessage = chatCompletionMessage;
            this.messageId = messageId;
        }

        private static LLMMessage from(ChatCompletionMessage chatCompletionMessage, String messageId) {
            return new OpenAILLMMessage(chatCompletionMessage, messageId);
        }

        @Override
        public List<ToolCall> toolCalls() {
            Optional<List<ChatCompletionMessageToolCall>> toolCalls = chatCompletionMessage.toolCalls();
            return toolCalls.map(chatCompletionMessageToolCalls ->
                    chatCompletionMessageToolCalls.stream().map(OpenAIToolCall::from)
                            .toList()).orElse(Collections.emptyList());
        }

        @Override
        public String content() {
            return chatCompletionMessage.content().orElse(null);
        }

        @Override
        public String id() {
            return messageId;
        }

        @Override
        public MessageType type() {
            return MessageType.AI;
        }
    }

    private static class OpenAIToolCall implements ToolCall {
        ChatCompletionMessageToolCall toolCall;

        private OpenAIToolCall(ChatCompletionMessageToolCall toolCall) {
            this.toolCall = toolCall;
        }

        private static ToolCall from(ChatCompletionMessageToolCall toolCall) {
            return new OpenAIToolCall(toolCall);
        }

        @Override
        public String id() {
            if (toolCall.isFunction()) {
                return toolCall.asFunction().id();
            } else {
                return toolCall.asCustom().id();
            }
        }

        @Override
        public String name() {
            if (toolCall.isFunction()) {
                return toolCall.asFunction().function().name();
            } else {
                return toolCall.asCustom().custom().name();
            }
        }

        @Override
        public String arguments() {
            if (toolCall.isFunction()) {
                return toolCall.asFunction().function().arguments();
            } else {
                return toolCall.asCustom().custom().input();
            }
        }
    }

    private static class OpenAILLMMessageChunk implements LLMMessageChunk {
        private final StringBuilder content;
        private final StringBuilder thinkingContent;
        private final Map<Integer, OpenAIToolCallChunkBuilder> toolCallBuilder;
        private final String messageId;

        private OpenAILLMMessageChunk(String messageId) {
            this.content = new StringBuilder();
            this.thinkingContent = new StringBuilder();
            this.toolCallBuilder = new HashMap<>();
            this.messageId = messageId;
        }

        private void mergeToolCallChunk(int index, String id, String name, String arguments) {
            OpenAIToolCallChunkBuilder builder = toolCallBuilder.computeIfAbsent(index, k -> OpenAIToolCallChunkBuilder.builder());
            builder.appendId(id)
                    .appendName(name)
                    .appendArguments(arguments);
        }

        private void mergeContent(String content) {
            if (content != null) {
                this.content.append(content);
            }
        }

        private void mergeThinkingContent(String thinking) {
            if (thinking != null) {
                this.thinkingContent.append(thinking);
            }
        }

        @Override
        public String id() {
            return messageId;
        }

        @Override
        public List<ToolCallChunk> toolCalls() {
            if (toolCallBuilder.isEmpty()) {
                return List.of();
            }
            return toolCallBuilder.entrySet().stream().sorted().map(item -> item.getValue().build(item.getKey()))
                    .toList();
        }

        @Override
        public String content() {
            return content.toString();
        }

        @Override
        public String thinkingContent() {
            String t = thinkingContent.toString();
            return t.isEmpty() ? null : t;
        }
    }


    private static class OpenAIToolCallChunk implements ToolCallChunk {
        int index;
        String id;
        String name;
        String arguments;

        public OpenAIToolCallChunk(int index, String id, String name, String arguments) {
            this.index = index;
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String arguments() {
            return arguments;
        }
    }

    private static class OpenAIToolCallChunkBuilder {
        StringBuilder idBuilder;
        StringBuilder nameBuilder;
        StringBuilder argumentsBuilder;

        private static OpenAIToolCallChunkBuilder builder() {
            OpenAIToolCallChunkBuilder builder = new OpenAIToolCallChunkBuilder();
            builder.idBuilder = new StringBuilder();
            builder.nameBuilder = new StringBuilder();
            builder.argumentsBuilder = new StringBuilder();
            return builder;
        }

        private OpenAIToolCallChunkBuilder appendId(String id) {
            if (id == null) {
                return this;
            }
            this.idBuilder.append(id);
            return this;
        }

        private OpenAIToolCallChunkBuilder appendName(String name) {
            if (name == null) {
                return this;
            }
            this.nameBuilder.append(name);
            return this;
        }

        private OpenAIToolCallChunkBuilder appendArguments(String arguments) {
            if (arguments == null) {
                return this;
            }
            this.argumentsBuilder.append(arguments);
            return this;
        }

        private ToolCallChunk build(int index) {
            return new OpenAIToolCallChunk(index, this.idBuilder.toString(),
                    this.nameBuilder.toString(), this.argumentsBuilder.toString());
        }
    }


    private static ChatCompletionFunctionTool convertToolDefinition(
            ToolDefinition toolDef) {
        Map<String, Object> propertiesMap = new HashMap<>();
        List<String> requiredParams = new ArrayList<>();

        if (toolDef.parameters() != null) {
            for (ToolParameterDefinition paramDef : toolDef.parameters()) {
                propertiesMap.put(paramDef.name(), ToolParameterType.toJsonSchema(paramDef.type(), paramDef.description()));
                if (paramDef.required()) {
                    requiredParams.add(paramDef.name());
                }
            }
        }

        FunctionDefinition functionDef = FunctionDefinition.builder()
                .name(toolDef.name())
                .description(toolDef.description())
                .parameters(FunctionParameters.builder()
                        .putAdditionalProperty("type", com.openai.core.JsonValue.from("object"))
                        .putAdditionalProperty("properties", com.openai.core.JsonValue.from(propertiesMap))
                        .putAdditionalProperty("required", com.openai.core.JsonValue.from(requiredParams))
                        .putAdditionalProperty("additionalProperties", com.openai.core.JsonValue.from(false))
                        .build())
                .build();

        return ChatCompletionFunctionTool.builder()
                .function(functionDef)
                .build();
    }

    private static ChatCompletionMessageParam convertToChatCompletionMessage(LLMMessage llmMessage) {
        if (llmMessage instanceof OpenAILLMMessage) {
            return ChatCompletionMessageParam.ofAssistant(((OpenAILLMMessage) llmMessage).chatCompletionMessage.toParam());
        }
        String content = llmMessage.content() == null ? "" : llmMessage.content();
        // Convert according to the message type.
        switch (llmMessage.type()) {
            case AI:
                ChatCompletionAssistantMessageParam.Builder params = ChatCompletionAssistantMessageParam.builder().content(content);
                if (llmMessage.toolCalls() != null) {
                    for (ToolCall toolCall : llmMessage.toolCalls()) {
                        if (toolCall == null || toolCall.name() == null || toolCall.arguments() == null) {
                            continue;
                        }
                        ChatCompletionMessageFunctionToolCall.Function function = ChatCompletionMessageFunctionToolCall.Function.builder()
                                .arguments(toolCall.arguments())
                                .name(toolCall.name())
                                .build();
                        String toolCallId = toolCall.id();
                        if (toolCallId == null || toolCallId.isBlank()) {
                            toolCallId = MessageIdGenerator.generateWithPrefix("tool-call");
                        }
                        ChatCompletionMessageFunctionToolCall chatCompletionMessageFunctionToolCall = ChatCompletionMessageFunctionToolCall.builder()
                                .id(toolCallId)
                                .function(function)
                                .build();
                        params.addToolCall(chatCompletionMessageFunctionToolCall);
                    }
                }
                return ChatCompletionMessageParam.ofAssistant(params.build());
            case HUMAN:
                return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(content).build());
            case SYSTEM:
                return ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(content).build());
            case TOOL:
                return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder().toolCallId(llmMessage.toolCallId())
                        .content(content).build());
        }
        return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(content).build());
    }
}
