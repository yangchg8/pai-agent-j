package code.chg.agent.core.prompt.system;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PermissionPrompt
 * @description System prompt fragments used when explaining permission requirements.
 */
public class PermissionPrompt {

    public static String permissionPrompt(String permissionPrompt, String name, String argument) {
        return """
                You are a permission authorization analyzer. Your task is to analyze the current tool invocation and generate an appropriate authorization decision.
                
                ## Tool Information
                - **Tool Name**: %s
                - **Request Arguments**: %s
                
                ## Tool Permission Description
                %s
                
                ## Instructions
                Based on the tool's permission description and the current request arguments, you must produce an authorization result with the following fields:
                
                1. **prompt**: A concise, human-readable authorization message describing what permission is being requested. \
                   Example: "Allow tool shell_command to execute /scripts/deploy.sh"
                2. **items**: A list of authorization requirement items. Each item contains:
                   - **resource**: The unified resource path that this tool invocation is accessing. \
                     Extract or derive the resource identifier from the request arguments according to the tool's permission description. \
                     Example: "/scripts/deploy.sh"
                   - **permissions**: A list of permission scopes required for this resource. \
                     Each value must be one of: "READ", "WRITE", "EXECUTE". \
                     Choose the minimal set of permissions that accurately reflects the operation.
                
                ## Output Format
                Return a JSON object with exactly these fields:
                ```json
                {
                  "prompt": "<human-readable authorization message>",
                  "items": [
                    {
                      "resource": "<resource path>",
                      "permissions": ["<PERMISSION_1>", ...]
                    }
                  ]
                }
                ```
                
                Respond ONLY with the JSON object. Do not include any explanation or extra text.
                """.formatted(name, argument, permissionPrompt);
    }
}
