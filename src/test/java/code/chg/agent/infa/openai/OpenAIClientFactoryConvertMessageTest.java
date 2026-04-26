package code.chg.agent.infa.openai;

import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.LLMRequest;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.ToolCall;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title OpenAIClientFactoryConvertMessageTest
 * @description Provides the OpenAIClientFactoryConvertMessageTest implementation.
 */
public class OpenAIClientFactoryConvertMessageTest {

    @Test
    public void assistantToolCallsShouldUsePerToolCallId() throws Exception {
        LLMMessage message = new LLMMessage() {
            @Override
            public String id() {
                return "ai-msg-1";
            }

            @Override
            public MessageType type() {
                return MessageType.AI;
            }

            @Override
            public String content() {
                return "";
            }

            @Override
            public List<ToolCall> toolCalls() {
                return List.of(new ToolCall() {
                    @Override
                    public String id() {
                        return "tool-call-123";
                    }

                    @Override
                    public String name() {
                        return "shell";
                    }

                    @Override
                    public String arguments() {
                        return "{\"command\":\"pwd\"}";
                    }
                });
            }
        };

        Method convert = OpenAIClientFactory.class.getDeclaredMethod("convertToChatCompletionMessage", LLMMessage.class);
        convert.setAccessible(true);
        ChatCompletionMessageParam param = (ChatCompletionMessageParam) convert.invoke(null, message);

        ChatCompletionAssistantMessageParam assistant = param.asAssistant();
        List<com.openai.models.chat.completions.ChatCompletionMessageToolCall> toolCalls = assistant.toolCalls().orElse(List.of());
        assertEquals(1, toolCalls.size());
        assertEquals("tool-call-123", toolCalls.get(0).asFunction().id());
    }

    @Test
    public void assistantToolCallsShouldGenerateFallbackIdWhenMissing() throws Exception {
        LLMMessage message = new LLMMessage() {
            @Override
            public String id() {
                return "ai-msg-2";
            }

            @Override
            public MessageType type() {
                return MessageType.AI;
            }

            @Override
            public String content() {
                return "";
            }

            @Override
            public List<ToolCall> toolCalls() {
                return List.of(new ToolCall() {
                    @Override
                    public String id() {
                        return null;
                    }

                    @Override
                    public String name() {
                        return "shell";
                    }

                    @Override
                    public String arguments() {
                        return "{\"command\":\"pwd\"}";
                    }
                });
            }
        };

        Method convert = OpenAIClientFactory.class.getDeclaredMethod("convertToChatCompletionMessage", LLMMessage.class);
        convert.setAccessible(true);
        ChatCompletionMessageParam param = (ChatCompletionMessageParam) convert.invoke(null, message);

        ChatCompletionAssistantMessageParam assistant = param.asAssistant();
        List<com.openai.models.chat.completions.ChatCompletionMessageToolCall> toolCalls = assistant.toolCalls().orElse(List.of());
        assertEquals(1, toolCalls.size());
        assertNotNull(toolCalls.get(0).asFunction().id());
    }

    @Test
    public void systemMessageWithNullContentShouldNotThrow() throws Exception {
        LLMMessage message = new LLMMessage() {
            @Override
            public String id() {
                return "system-msg-1";
            }

            @Override
            public MessageType type() {
                return MessageType.SYSTEM;
            }

            @Override
            public String content() {
                return null;
            }
        };

        Method convert = OpenAIClientFactory.class.getDeclaredMethod("convertToChatCompletionMessage", LLMMessage.class);
        convert.setAccessible(true);
        ChatCompletionMessageParam param = (ChatCompletionMessageParam) convert.invoke(null, message);

        assertNotNull(param);
        assertNotNull(param.asSystem());
    }

    @Test
    public void streamChatParamsShouldRequestUsageInStreamingResponses() throws Exception {
        LLMRequest request = new LLMRequest(List.of(new LLMMessage() {
            @Override
            public String id() {
                return "user-msg-1";
            }

            @Override
            public MessageType type() {
                return MessageType.HUMAN;
            }

            @Override
            public String content() {
                return "hello";
            }
        }));

        Class<?> clientClass = Class.forName("code.chg.agent.infa.openai.OpenAIClientFactory$OpenAILLMClient");
        Method createParams = clientClass.getDeclaredMethod("createOpenAIParams", LLMRequest.class);
        createParams.setAccessible(true);
        ChatCompletionCreateParams params = (ChatCompletionCreateParams) createParams.invoke(null, request);

        assertEquals(Boolean.TRUE, params.streamOptions().flatMap(option -> option.includeUsage()).orElse(false));
    }
}