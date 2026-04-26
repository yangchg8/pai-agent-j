package code.chg.agent.cli.command;

import code.chg.agent.cli.render.LessRenderer;
import code.chg.agent.lib.agent.PaiAgent;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionCommandContext
 * @description Defines the SessionCommandContext record.
 */
public record SessionCommandContext(PaiAgent agent, LessRenderer renderer) {
}