package code.chg.agent.core.session;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionSummaryData
 * @description Provides the SessionSummaryData implementation.
 */
@Data
public class SessionSummaryData {
    private String sessionId;
    private String title;
    private String latestUserMessage;
    private Long createdAt;
    private Long lastActiveAt;
    private Integer latestTokenCount;
}