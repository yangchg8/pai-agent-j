package code.chg.agent.utils;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.core.tool.AbstractTool;
import code.chg.agent.core.tool.DefaultToolBuilder;
import code.chg.agent.llm.ToolDefinition;
import code.chg.agent.llm.ToolParameterDefinition;
import code.chg.agent.llm.ToolParameterType;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolUtil
 * @description Reflectively parses {@link Tool} and {@link ToolParameter} annotations from a class
 */
public final class ToolUtil {

    private ToolUtil() {
    }

    /**
     * Finds the method annotated with {@code @Tool(name = toolName)} in {@code clazz} and
     * returns a {@link ToolDefinition} built from its annotations.
     *
     * @param clazz    class to inspect
     * @param toolName the value of {@link Tool#name()} to match
     * @return the matching definition, or empty if no such method is found
     */
    public static Optional<ToolDefinition> findToolDefinition(Class<?> clazz, String toolName) {
        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null || !toolName.equals(toolAnnotation.name())) {
                continue;
            }
            String description = toolAnnotation.description();
            List<ToolParameterDefinition> paramDefs = buildParameterDefinitions(method);
            return Optional.of(new SimpleToolDefinition(toolName, description, paramDefs));
        }
        return Optional.empty();
    }

    /**
     * Builds all tools declared via {@link Tool} annotations on the given class.
     *
     * @param clazz    class that contains tool methods
     * @param instance receiver object for instance methods; may be null for static-only tool classes
     * @return all built tools in declaration scan order
     */
    public static List<code.chg.agent.core.tool.Tool> buildTools(Class<?> clazz, Object instance) {
        if (clazz == null) {
            return List.of();
        }
        List<code.chg.agent.core.tool.Tool> tools = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
            DefaultToolBuilder builder = AbstractTool.builder(toolName)
                    .description(toolAnnotation.description());

            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                ToolParameter parameterAnnotation = parameter.getAnnotation(ToolParameter.class);
                String parameterName = parameterAnnotation != null && !parameterAnnotation.name().isEmpty()
                        ? parameterAnnotation.name()
                        : parameter.getName();
                String parameterDescription = parameterAnnotation != null ? parameterAnnotation.description() : "";
                builder.parameter(i, parameterName, parameterDescription);
            }

            builder.bindMethod(instance, method);
            builder.bindPermissionChecker(clazz, instance);
            tools.add(builder.build());
        }
        return tools;
    }

    private static List<ToolParameterDefinition> buildParameterDefinitions(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ToolParameterDefinition> result = new ArrayList<>(parameters.length);
        for (Parameter param : parameters) {
            ToolParameter ann = param.getAnnotation(ToolParameter.class);
            String name = (ann != null && !ann.name().isEmpty()) ? ann.name() : param.getName();
            String desc = (ann != null) ? ann.description() : "";
            ToolParameterType type = ToolParameterType.from(param.getType());
            result.add(new SimpleToolParameterDefinition(name, desc, type));
        }
        return result;
    }

    private record SimpleToolDefinition(
            String name,
            String description,
            List<ToolParameterDefinition> parameters
    ) implements ToolDefinition {
    }

    private record SimpleToolParameterDefinition(
            String name,
            String description,
            ToolParameterType type
    ) implements ToolParameterDefinition {
        @Override
        public boolean required() {
            return true;
        }
    }
}
