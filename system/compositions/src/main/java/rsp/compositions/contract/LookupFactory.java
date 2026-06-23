package rsp.compositions.contract;

import rsp.component.*;

/**
 * Factory for creating Lookup instances from ComponentContext.
 * Bridges framework infrastructure (ComponentContext, CommandsEnqueue, Subscriber)
 * to the contract API (Lookup).
 */
public final class LookupFactory {
    private LookupFactory() {}

    /**
     * Create a Lookup from ComponentContext for contract instantiation.
     * Retrieves CommandsEnqueue and Subscriber from the context.
     */
    public static Lookup create(ComponentContext context) {
        CommandsEnqueue commandsEnqueue = context.getRequired(CommandsEnqueue.class);
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * Create a scope-backed Lookup for a long-lived contract runtime.
     * Infrastructure objects still come from the current framework context.
     */
    public static Lookup create(ContextScope contextScope, ComponentContext context) {
        CommandsEnqueue commandsEnqueue = context.getRequired(CommandsEnqueue.class);
        return create(contextScope, context, commandsEnqueue);
    }

    /**
     * Create a Lookup with explicit CommandsEnqueue (for event handlers
     * where the framework provides a specific CommandsEnqueue instance).
     */
    public static Lookup create(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * Create a scope-backed Lookup with explicit CommandsEnqueue.
     */
    public static Lookup create(ContextScope contextScope,
                                ComponentContext context,
                                CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(contextScope, commandsEnqueue, subscriber);
    }
}
