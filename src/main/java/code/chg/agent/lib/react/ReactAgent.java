package code.chg.agent.lib.react;

import code.chg.agent.core.agent.Agent;
import code.chg.agent.core.brain.DefaultBrain;
import code.chg.agent.core.channel.ChannelSubscriber;
import code.chg.agent.core.channel.DefaultChatChannel;
import code.chg.agent.core.event.DefaultEventBusContext;
import code.chg.agent.core.event.BaseEventMessageBus;
import code.chg.agent.core.event.EventMessageBus;
import code.chg.agent.core.event.Subscription;
import code.chg.agent.core.event.message.AuthorizationResponseEventMessage;
import code.chg.agent.core.event.message.HumanEventMessage;
import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.memory.SystemMemoryRegion;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermission;
import code.chg.agent.infa.mcp.McpServerConfig;
import code.chg.agent.infa.mcp.McpToolAdapter;
import code.chg.agent.lib.brain.BrainSummaryHook;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.utils.ToolUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ReactAgent
 * @description ReAct-style agent that wires together the event bus, brain, memory regions, and tools.
 */
@Getter
public class ReactAgent implements Agent {
    private final EventMessageBus messageBus;
    private final List<Subscription> subscriptions;
    private final DefaultBrain mainBrain;

    protected ReactAgent(EventMessageBus eventMessageBus, DefaultBrain mainBrain) {
        messageBus = eventMessageBus;
        subscriptions = new ArrayList<>();
        this.mainBrain = mainBrain;
    }

    public boolean registerSubscription(Subscription subscription) {
        if (messageBus.subscribe(subscription)) {
            subscriptions.add(subscription);
            return true;
        }
        return false;
    }

    @Override
    public void talk(String message, ChannelSubscriber channelSubscriber) {
        DefaultChatChannel channel = new DefaultChatChannel();
        if (channelSubscriber != null) {
            channel.subscribe(channelSubscriber);
        }
        messageBus.talk(new HumanEventMessage(message), channel);
        channel.await();
    }

    @Override
    public void authorize(AuthorizationResponseEventBody body, ChannelSubscriber channelSubscriber) {
        if (body.id() == null || body.scope() == null) {
            return;
        }
        DefaultChatChannel channel = new DefaultChatChannel();
        if (channelSubscriber != null) {
            channel.subscribe(channelSubscriber);
        }
        messageBus.talk(new AuthorizationResponseEventMessage(body), channel);
        channel.await();
    }

    public void compact(ChannelSubscriber channelSubscriber) {
        DefaultChatChannel channel = new DefaultChatChannel();
        if (channelSubscriber != null) {
            channel.subscribe(channelSubscriber);
        }
        if (mainBrain.brainHook() != null) {
            if (mainBrain.brainHook() instanceof BrainSummaryHook summaryHook) {
                summaryHook.cleanUp(mainBrain, new DefaultEventBusContext(channel), true);
            } else {
                mainBrain.brainHook().cleanUp(mainBrain, new DefaultEventBusContext(channel));
            }
        }
        if (messageBus instanceof BaseEventMessageBus bus) {
            bus.saveStateNow();
        }
        channel.close();
        channel.await();
    }

    public static ReactAgentBuilder builder(String name) {
        return new ReactAgentBuilder(name);
    }

    public static class ReactAgentBuilder {
        List<Tool> tools;
        List<McpToolAdapter> mcpToolAdapters;
        EventMessageBus eventMessageBus;
        String systemPrompt;
        String name;
        List<ToolPermission> globalResourcePermissions = new ArrayList<>();
        Set<Permission> globalPermissionLevels = new HashSet<>();
        List<MemoryRegion> extraMemoryRegions = new ArrayList<>();

        private ReactAgentBuilder(String name) {
            this.name = name;
        }

        public ReactAgentBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public ReactAgentBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Add a globally pre-authorized resource permission.
         * Resources in this list are treated as already granted for all tools.
         * Example: {@code globalPermission("DIR:/workdir/**", List.of(Permission.ALL))}
         */
        public ReactAgentBuilder globalPermission(String resource, List<Permission> permissions) {
            if (resource != null && permissions != null && !permissions.isEmpty()) {
                ToolPermission tp = new ToolPermission(resource, new HashSet<>(permissions));
                globalResourcePermissions.add(tp);
            }
            return this;
        }

        /**
         * Add one or more globally auto-approved permission levels.
         * Any tool invocation that only requires these permission levels is granted automatically.
         * Example: {@code globalPermissionLevel(Permission.READ)} means no auth needed for read-only ops.
         */
        public ReactAgentBuilder globalPermissionLevel(Permission... levels) {
            if (levels != null) {
                globalPermissionLevels.addAll(Arrays.asList(levels));
            }
            return this;
        }

        public ReactAgentBuilder bindTool(Tool tool) {
            if (tools == null) {
                tools = new ArrayList<>();
            }
            tools.add(tool);
            return this;
        }

        public ReactAgentBuilder bindMemoryRegion(MemoryRegion memoryRegion) {
            if (memoryRegion != null) {
                extraMemoryRegions.add(memoryRegion);
            }
            return this;
        }

        public ReactAgentBuilder bindMcp(McpServerConfig config) {
            if (config == null) {
                return this;
            }
            return bindMcp(new McpToolAdapter(config));
        }

        public ReactAgentBuilder bindMcp(McpToolAdapter adapter) {
            if (adapter == null) {
                return this;
            }
            if (mcpToolAdapters == null) {
                mcpToolAdapters = new ArrayList<>();
            }
            mcpToolAdapters.add(adapter);
            return this;
        }

        public ReactAgentBuilder bindTools(Object o) {
            if (o instanceof McpServerConfig config) {
                return bindMcp(config);
            }
            if (o instanceof McpToolAdapter adapter) {
                return bindMcp(adapter);
            }

            Class<?> clazz;
            Object instance = o;
            if (o instanceof Class<?>) {
                clazz = (Class<?>) o;
                instance = null;
            } else {
                clazz = o.getClass();
            }

            for (Tool tool : ToolUtil.buildTools(clazz, instance)) {
                bindTool(tool);
            }
            return this;
        }

        public ReactAgentBuilder messageBus(EventMessageBus eventMessageBus) {
            this.eventMessageBus = eventMessageBus;
            return this;
        }

        public ReactAgent build() {
            if (this.eventMessageBus == null) {
                throw new IllegalStateException("EventMessageBus is required to build ReactAgent");
            }
            DefaultBrain mainBrain = new DefaultBrain(this.name);
            if (!globalResourcePermissions.isEmpty() || !globalPermissionLevels.isEmpty()) {
                mainBrain.getBrainRunningState().setGlobalConfig(globalResourcePermissions, globalPermissionLevels);
            }
            SystemMemoryRegion systemMemoryRegion = new SystemMemoryRegion(this.systemPrompt);
            List<MemoryRegion> memoryRegions = new ArrayList<>();
            memoryRegions.add(systemMemoryRegion);
            memoryRegions.addAll(extraMemoryRegions);
            mainBrain.setMemoryRegions(memoryRegions);

            ReactAgent agent = new ReactAgent(this.eventMessageBus, mainBrain);


            List<Tool> boundTools = new ArrayList<>();
            if (this.tools != null) {
                boundTools.addAll(this.tools);
            }
            if (this.mcpToolAdapters != null) {
                for (McpToolAdapter adapter : this.mcpToolAdapters) {
                    boundTools.addAll(adapter.discoverTools());
                }
            }
            for (Tool tool : boundTools) {
                systemMemoryRegion.bindTool(tool);
                agent.registerSubscription(tool);
            }

            if (!memoryRegions.isEmpty()) {
                for (MemoryRegion memoryRegion : memoryRegions) {
                    List<Tool> tools = memoryRegion.tools();
                    if (tools != null) {
                        tools.forEach(agent::registerSubscription);
                    }
                }
            }

            mainBrain.setBrainHook(new BrainSummaryHook());
            agent.registerSubscription(mainBrain);
            return agent;
        }
    }
}
