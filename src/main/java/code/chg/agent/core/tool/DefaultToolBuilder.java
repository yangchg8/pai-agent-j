package code.chg.agent.core.tool;

import code.chg.agent.annotation.ToolPermissionChecker;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.llm.ToolDefinition;
import code.chg.agent.llm.ToolParameterDefinition;
import code.chg.agent.llm.ToolParameterType;
import lombok.Setter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title DefaultToolBuilder
 * @description Builder for creating tool definitions from annotated Java methods.
 */
@Setter
public class DefaultToolBuilder {
    private final String name;
    private final Map<Integer, String> parameterDescriptions;
    private final Map<Integer, String> parameterNames;
    private String description;
    private Method method;
    private Object object;
    private PermissionChecker permissionChecker;

    public DefaultToolBuilder(String name) {
        this.name = name;
        this.parameterDescriptions = new HashMap<>();
        this.parameterNames = new HashMap<>();
        this.permissionChecker = null;

    }

    public DefaultToolBuilder description(String description) {
        this.description = description;
        return this;
    }

    public DefaultToolBuilder parameter(int index, String name, String description) {
        parameterDescriptions.put(index, description);
        parameterNames.put(index, name);
        return this;
    }

    public DefaultToolBuilder permissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
        return this;
    }

    public DefaultToolBuilder bindMethod(Object object, Method method) {
        this.object = object;
        this.method = method;
        return this;
    }

    public DefaultToolBuilder bindPermissionChecker(Class<?> clazz, Object instance) {
        Method checkerMethod = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(ToolPermissionChecker.class)) {
                continue;
            }
            ToolPermissionChecker annotation = m.getAnnotation(ToolPermissionChecker.class);
            if (!this.name.equals(annotation.toolName())) {
                continue;
            }
            if (checkerMethod != null) {
                throw new IllegalArgumentException(
                        "Duplicate @ToolPermissionChecker for tool '" + this.name + "' in class " + clazz.getName());
            }
            // Validate parameter count and types
            Parameter[] params = m.getParameters();
            if (params.length != 2) {
                throw new IllegalArgumentException(
                        "@ToolPermissionChecker method '" + m.getName() + "' for tool '" + this.name
                                + "' must have exactly 2 parameters (ToolPermissionPolicy, Object[]), but found " + params.length);
            }
            if (!ToolPermissionPolicy.class.isAssignableFrom(params[0].getType())) {
                throw new IllegalArgumentException(
                        "@ToolPermissionChecker method '" + m.getName() + "' for tool '" + this.name
                                + "': first parameter must be ToolPermissionPolicy, but found " + params[0].getType().getName());
            }
            if (!Object[].class.isAssignableFrom(params[1].getType())) {
                throw new IllegalArgumentException(
                        "@ToolPermissionChecker method '" + m.getName() + "' for tool '" + this.name
                                + "': second parameter must be Object[], but found " + params[1].getType().getName());
            }
            if (!(ToolPermissionResult.class.isAssignableFrom(m.getReturnType()))) {
                throw new IllegalArgumentException(
                        "@ToolPermissionChecker method '" + m.getName() + "' for tool '" + this.name
                                + "': return type must be ToolPermissionResult, but found " + params[1].getType().getName());
            }
            checkerMethod = m;
        }
        if (checkerMethod != null) {
            Method finalCheckerMethod = checkerMethod;
            this.permissionChecker = (policy, arguments) -> {
                try {
                    return (ToolPermissionResult) finalCheckerMethod.invoke(instance, policy, arguments);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
        return this;
    }

    public AbstractTool build() {
        if (method == null) {
            throw new IllegalArgumentException("Method must be provided");
        }
        Parameter[] parameters = method.getParameters();
        DefaultToolDefinition toolDefinition = new DefaultToolDefinition(name, description);
        if (parameters == null || parameters.length == 0) {
            return new BaseTool(toolDefinition, this.object, this.method, this.permissionChecker);
        }
        List<ToolParameterDefinition> parameterDefinitions = new ArrayList<>();
        for (int index = 0; index < parameters.length; index++) {
            String description = parameterDescriptions.get(index);
            String name = parameterNames.get(index);
            Parameter parameter = parameters[index];
            DefaultToolParameterDefinition parameterDefinition = new DefaultToolParameterDefinition();
            parameterDefinition.setName(name);
            parameterDefinition.setRequired(true);
            parameterDefinition.setDescription(description);
            parameterDefinition.setType(ToolParameterType.from(parameter.getType()));
            parameterDefinitions.add(parameterDefinition);
        }
        toolDefinition.setParameterDefinitions(parameterDefinitions);
        return new BaseTool(toolDefinition, this.object, this.method, this.permissionChecker);
    }


    private static class DefaultToolDefinition implements ToolDefinition {
        private final String name;
        private final String description;
        @Setter
        private List<ToolParameterDefinition> parameterDefinitions;

        public DefaultToolDefinition(String name, String description) {
            this.name = name;
            this.description = description;
            this.parameterDefinitions = new ArrayList<>();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public List<ToolParameterDefinition> parameters() {
            return parameterDefinitions;
        }
    }

    @Setter
    private static class DefaultToolParameterDefinition implements ToolParameterDefinition {
        String name;
        String description;
        boolean required;
        ToolParameterType type;

        @Override
        public String description() {
            return description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean required() {
            return required;
        }

        @Override
        public ToolParameterType type() {
            return type;
        }
    }
}
