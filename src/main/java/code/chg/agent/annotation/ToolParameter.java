package code.chg.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolParameter
 * @description Annotation for describing tool method parameters
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParameter {
    /**
     * Parameter name
     */
    String name() default "";

    /**
     * Parameter description
     */
    String description() default "";
}

