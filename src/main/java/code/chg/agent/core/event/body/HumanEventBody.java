package code.chg.agent.core.event.body;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title HumanEventBody
 * @description Event payload carrying user-provided text.
 */
@Data
public class HumanEventBody implements EventBody {
    private final String content;

    public HumanEventBody(String content) {
        this.content = content;
    }
}
