package code.chg.agent.core.brain.process;

import code.chg.agent.llm.LLMMessage;
import lombok.*;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessageResponse
 * @description Result object returned by an event-message processor.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventMessageResponse {
    List<LLMMessage> messages;
    boolean callLLM;
    String breakReason;
}
