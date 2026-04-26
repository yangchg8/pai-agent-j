package code.chg.agent.lib.tool.shell.permission;

import code.chg.agent.lib.tool.shell.safety.CommandUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandPermissionRegistry
 * @description Provides the ShellCommandPermissionRegistry implementation.
 */
public final class ShellCommandPermissionRegistry {

    private final Map<String, ShellCommandPermissionHook> hooks = new LinkedHashMap<>();

    public static ShellCommandPermissionRegistry createDefault() {
        ShellCommandPermissionRegistry registry = new ShellCommandPermissionRegistry();
        ShellBuiltinPermissionHooks.registerDefaults(registry);
        return registry;
    }

    public ShellCommandPermissionRegistry register(String commandName, ShellCommandPermissionHook hook) {
        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("commandName must not be blank");
        }
        if (hook == null) {
            throw new IllegalArgumentException("hook must not be null");
        }
        hooks.put(normalize(commandName), hook);
        return this;
    }

    public ShellCommandPermissionRegistry registerAll(Collection<String> commandNames,
                                                      ShellCommandPermissionHook hook) {
        if (commandNames == null) {
            return this;
        }
        for (String commandName : commandNames) {
            register(commandName, hook);
        }
        return this;
    }

    public Optional<ShellCommandPermissionHook> findHook(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(hooks.get(normalize(commandName)));
    }

    public boolean hasHook(String commandName) {
        return findHook(commandName).isPresent();
    }

    private static String normalize(String commandName) {
        String executableName = CommandUtils.executableNameLookupKey(commandName);
        return executableName == null ? commandName : executableName;
    }
}