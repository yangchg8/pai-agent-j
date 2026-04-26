package code.chg.agent.core.session;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title HistoryMessageData
 * @description Provides the HistoryMessageData implementation.
 */
@Data
public class HistoryMessageData {
    private String sessionId;
    private String messageId;
    private String role;
    private String channelMessageType;
    private String content;
    private String bodyJson;
    private String toolCallId;
    private String toolName;
    private Integer renderOrder;
    private Boolean completed;
    private Long createdAt;
}