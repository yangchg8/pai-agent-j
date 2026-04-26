package code.chg.agent.lib.tool.shell.safety;

import code.chg.agent.core.permission.Permission;
import lombok.Getter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellAnalysisResult
 * @description Result of static analysis of a shell command/script.
 */
@Getter
public class ShellAnalysisResult {

    public enum Status {
        ANALYZED,
        SCRIPT_EXECUTION,
        NOT_IN_WHITELIST,
        UNPARSEABLE
    }

    private final Status status;
    private final List<ResourcePermission> resourcePermissions;
    private final List<List<String>> parsedCommands;
    private final String unrecognizedCommand;
    private final String rawCommand;
    private final int commandCount;

    private ShellAnalysisResult(Status status,
                                List<ResourcePermission> resourcePermissions,
                                List<List<String>> parsedCommands,
                                String unrecognizedCommand,
                                String rawCommand,
                                int commandCount) {
        this.status = status;
        this.resourcePermissions = resourcePermissions != null ? resourcePermissions : new ArrayList<>();
        this.parsedCommands = parsedCommands != null ? parsedCommands : new ArrayList<>();
        this.unrecognizedCommand = unrecognizedCommand;
        this.rawCommand = rawCommand;
        this.commandCount = commandCount;
    }

    public static ShellAnalysisResult analyzed(List<ResourcePermission> resourcePermissions, String rawCommand) {
        return analyzed(resourcePermissions, rawCommand, List.of());
    }

    public static ShellAnalysisResult analyzed(List<ResourcePermission> resourcePermissions,
                                               String rawCommand,
                                               List<List<String>> parsedCommands) {
        return new ShellAnalysisResult(Status.ANALYZED, resourcePermissions, parsedCommands, null, rawCommand, 0);
    }

    public static ShellAnalysisResult scriptExecution(List<ResourcePermission> resourcePermissions, String rawCommand) {
        return scriptExecution(resourcePermissions, rawCommand, List.of());
    }

    public static ShellAnalysisResult scriptExecution(List<ResourcePermission> resourcePermissions,
                                                      String rawCommand,
                                                      List<List<String>> parsedCommands) {
        return new ShellAnalysisResult(Status.SCRIPT_EXECUTION, resourcePermissions, parsedCommands, null, rawCommand, 1);
    }

    public static ShellAnalysisResult notInWhitelist(String unrecognizedCommand, String rawCommand, int commandCount) {
        return notInWhitelist(unrecognizedCommand, rawCommand, commandCount, List.of());
    }

    public static ShellAnalysisResult notInWhitelist(String unrecognizedCommand,
                                                     String rawCommand,
                                                     int commandCount,
                                                     List<List<String>> parsedCommands) {
        List<ResourcePermission> commandLevelPermissions = new ArrayList<>();
        commandLevelPermissions.add(new ResourcePermission(
                "COMMAND:" + unrecognizedCommand, EnumSet.of(Permission.ALL)));
        return new ShellAnalysisResult(Status.NOT_IN_WHITELIST, commandLevelPermissions,
                parsedCommands, unrecognizedCommand, rawCommand, commandCount);
    }

    public static ShellAnalysisResult unparseable(String rawCommand) {
        return unparseable(rawCommand, List.of());
    }

    public static ShellAnalysisResult unparseable(String rawCommand, List<List<String>> parsedCommands) {
        return new ShellAnalysisResult(Status.UNPARSEABLE, null, parsedCommands, null, rawCommand, 0);
    }
}
