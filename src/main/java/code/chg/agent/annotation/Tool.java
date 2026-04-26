package code.chg.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title Tool
 * @description Annotation for marking a method as a tool
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    /**
     * Tool name
     */
    String name() default "";

    /**
     * Tool description
     */
    String description() default "";
}

