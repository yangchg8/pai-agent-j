package code.chg.agent.lib.prompt;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CompactPrompts
 * @description Prompt constants for context checkpoint compaction (summary/compression).
 */
public final class CompactPrompts {

    /**
     * The summarization prompt sent to the LLM to generate a handoff summary
     * when the conversation context exceeds the token limit.
     */
    public static final String SUMMARIZATION_PROMPT = """
            You are performing a CONTEXT CHECKPOINT COMPACTION. Create a handoff summary for another LLM that will resume the task.
            
            Include:
            - Current progress and key decisions made
            - Important context, constraints, or user preferences
            - What remains to be done (clear next steps)
            - Any critical data, examples, or references needed to continue
            
            Be concise, structured, and focused on helping the next LLM seamlessly continue the work.""";

    /**
     * Prefix prepended to the summary text when it is inserted into the compacted
     * conversation history. Tells the subsequent LLM that this is a handoff summary
     * from a prior model run.
     */
    public static final String SUMMARY_PREFIX = """
            Another language model started to solve this problem and produced a summary of its thinking process. \
            You also have access to the state of the tools that were used by that language model. \
            Use this to build on the work that has already been done and avoid duplicating work. \
            Here is the summary produced by the other language model, use the information in this summary to assist with your own analysis:""";

    private CompactPrompts() {
    }
}
