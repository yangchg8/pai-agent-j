package code.chg.agent.llm;

import lombok.Getter;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolParameterBaseType
 * @description Enumeration of primitive schema base types supported by tool parameters.
 */
@Getter
public enum ToolParameterBaseType {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
    NUMBER("number"),
    ARRAY("array"),
    OBJECT("object");
    final String type;

    ToolParameterBaseType(String type) {
        this.type = type;
    }
}
