package code.chg.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolPermissionChecker
 * @description Annotation for declaring the permission checker associated with a tool.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolPermissionChecker {
    String toolName() default "";
}
