package rsp.compositions.agent;

import rsp.compositions.block.Block;

import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockMetadata;
import rsp.compositions.block.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.composition.StructureNode;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceUtilsTests {

    private static final EventKey.VoidKey CREATE_KEY = new EventKey.VoidKey("test.create");
    private static final EventKey.SimpleKey<Integer> PAGE_KEY =
        new EventKey.SimpleKey<>("test.page", Integer.class);

    private static final List<BlockAction> ACTIONS = List.of(
        new BlockAction("create", CREATE_KEY, "Create item"),
        new BlockAction("page", PAGE_KEY, "Go to page",
            new PayloadSchema.IntegerValue("page number"))
    );

    private static final StructureNode TREE = new StructureNode("Root", null,
        List.of(new StructureNode("Posts", null, List.of(), List.of(StubBlock.class))),
        List.of());

    // --- buildToolDefinitions ---

    @Test
    void buildToolDefinitions_includes_actions_and_builtins() {
        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        List<ToolDefinition> tools = AgentServiceUtils.buildToolDefinitions(profile, TREE);

        // 2 actions + navigate + plan + text_reply
        assertEquals(5, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "create".equals(t.name())));
        assertTrue(tools.stream().anyMatch(t -> "page".equals(t.name())));
        assertTrue(tools.stream().anyMatch(t -> "navigate".equals(t.name())));
        assertTrue(tools.stream().anyMatch(t -> "plan".equals(t.name())));
        assertTrue(tools.stream().anyMatch(t -> "text_reply".equals(t.name())));
    }

    @Test
    void buildToolDefinitions_without_tree_omits_navigate() {
        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        List<ToolDefinition> tools = AgentServiceUtils.buildToolDefinitions(profile, null);

        assertEquals(4, tools.size());
        assertFalse(tools.stream().anyMatch(t -> "navigate".equals(t.name())));
    }

    @Test
    void buildClassificationTools_contains_only_plan_and_text_reply() {
        List<ToolDefinition> tools = AgentServiceUtils.buildClassificationTools();

        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "plan".equals(t.name())));
        assertTrue(tools.stream().anyMatch(t -> "text_reply".equals(t.name())));
    }

    // --- findAction ---

    @Test
    void findAction_returns_matching_action() {
        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        BlockAction found = AgentServiceUtils.findAction("page", profile);

        assertNotNull(found);
        assertEquals("page", found.action());
    }

    @Test
    void findAction_returns_null_for_unknown() {
        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        assertNull(AgentServiceUtils.findAction("unknown", profile));
    }

    // --- resolveTargetBlock ---

    @Test
    void resolveTargetBlock_exact_match() {
        Class<? extends Block<?, ?>> result =
            AgentServiceUtils.resolveTargetBlock("StubBlock", TREE);
        assertEquals(StubBlock.class, result);
    }

    @Test
    void resolveTargetBlock_fuzzy_match() {
        Class<? extends Block<?, ?>> result =
            AgentServiceUtils.resolveTargetBlock("Stub", TREE);
        assertEquals(StubBlock.class, result);
    }

    @Test
    void resolveTargetBlock_label_match() {
        Class<? extends Block<?, ?>> result =
            AgentServiceUtils.resolveTargetBlock("Posts", TREE);
        assertEquals(StubBlock.class, result);
    }

    @Test
    void resolveTargetBlock_returns_null_for_blank() {
        assertNull(AgentServiceUtils.resolveTargetBlock("", TREE));
        assertNull(AgentServiceUtils.resolveTargetBlock(null, TREE));
    }

    // --- toAgentResult ---

    @Test
    void toAgentResult_parses_plan() {
        JsonDataType.Object output = jsonObject(Map.of(
            "type", new JsonDataType.String("plan"),
            "steps", new JsonDataType.Array(
                new JsonDataType.String("step 1"),
                new JsonDataType.String("step 2")),
            "message", new JsonDataType.String("summary")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toAgentResult(output, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.PlanResult.class, result.get());
        AgentResult.PlanResult plan = (AgentResult.PlanResult) result.get();
        assertEquals(2, plan.steps().size());
        assertEquals("summary", plan.summary());
    }

    @Test
    void toAgentResult_parses_text_reply() {
        JsonDataType.Object output = jsonObject(Map.of(
            "type", new JsonDataType.String("text"),
            "message", new JsonDataType.String("Hello!")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toAgentResult(output, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.TextReply.class, result.get());
        assertEquals("Hello!", ((AgentResult.TextReply) result.get()).message());
    }

    @Test
    void toAgentResult_parses_action() {
        JsonDataType.Object output = jsonObject(Map.of(
            "type", new JsonDataType.String("intent"),
            "action", new JsonDataType.String("page"),
            "payload", JsonDataType.Number.of(3)));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toAgentResult(output, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.ActionResult.class, result.get());
        AgentResult.ActionResult ar = (AgentResult.ActionResult) result.get();
        assertEquals("page", ar.action().action());
    }

    @Test
    void toAgentResult_parses_navigate() {
        JsonDataType.Object output = jsonObject(Map.of(
            "type", new JsonDataType.String("intent"),
            "action", new JsonDataType.String("navigate"),
            "targetBlock", new JsonDataType.String("StubBlock")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toAgentResult(output, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.NavigateResult.class, result.get());
        assertEquals(StubBlock.class,
            ((AgentResult.NavigateResult) result.get()).targetBlock());
    }

    // --- toolUseToAgentResult ---

    @Test
    void toolUseToAgentResult_plan_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "steps", new JsonDataType.Array(
                new JsonDataType.String("open page 2"),
                new JsonDataType.String("select all")),
            "message", new JsonDataType.String("Will do")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("plan", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.PlanResult.class, result.get());
        AgentResult.PlanResult plan = (AgentResult.PlanResult) result.get();
        assertEquals(2, plan.steps().size());
        assertEquals("Will do", plan.summary());
    }

    @Test
    void toolUseToAgentResult_text_reply_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "message", new JsonDataType.String("Hi there!")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("text_reply", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.TextReply.class, result.get());
        assertEquals("Hi there!", ((AgentResult.TextReply) result.get()).message());
    }

    @Test
    void toolUseToAgentResult_navigate_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "targetBlock", new JsonDataType.String("StubBlock")));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("navigate", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.NavigateResult.class, result.get());
        assertEquals(StubBlock.class, ((AgentResult.NavigateResult) result.get()).targetBlock());
    }

    @Test
    void toolUseToAgentResult_action_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "payload", JsonDataType.Number.of(3)));

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("page", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.ActionResult.class, result.get());
        assertEquals("page", ((AgentResult.ActionResult) result.get()).action().action());
    }

    /** Regression: when an action declares an ObjectValue schema, the tool
     *  input puts the structured properties at the top level (correct JSON
     *  Schema for objects), NOT nested under a "payload" key. The conversion
     *  must use the whole input object, otherwise the downstream parser
     *  receives null and errors with "Expected Map<String, Object>, got null". */
    @Test
    void toolUseToAgentResult_action_tool_with_object_value_schema() {
        EventKey.SimpleKey<Map<String, Object>> setFieldKey =
            new EventKey.SimpleKey<>("test.set_field",
                (Class<Map<String, Object>>) (Class<?>) Map.class);
        BlockAction setField = new BlockAction("set_field", setFieldKey,
            "Set a single form field value",
            new PayloadSchema.ObjectValue(List.of(
                new PayloadSchema.Property("name", "string", true, "field name"),
                new PayloadSchema.Property("value", "string", true, "field value"))));
        BlockProfile profile = new BlockProfile(null, List.of(setField), StubBlock.class);

        // LLM emits the structured fields at the TOP LEVEL of the tool input.
        JsonDataType.Object input = jsonObject(Map.of(
            "name", new JsonDataType.String("title"),
            "value", new JsonDataType.String("Hello")));

        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult(
            "set_field", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.ActionResult.class, result.get());
        AgentResult.ActionResult ar = (AgentResult.ActionResult) result.get();
        assertEquals("set_field", ar.action().action());

        // The parser must produce a non-null payload that yields a usable Map.
        Object parsed = ar.action().parsePayload().apply(ar.payload());
        assertInstanceOf(Map.class, parsed);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals("title", map.get("name"));
        assertEquals("Hello", map.get("value"));
    }

    @Test
    void toolUseToAgentResult_unknown_tool_returns_text_reply() {
        JsonDataType.Object input = jsonObject(Map.of());

        BlockProfile profile = new BlockProfile(null, ACTIONS, StubBlock.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("unknown", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.TextReply.class, result.get());
    }

    // --- describeState ---

    @Test
    void describeState_with_items() {
        BlockMetadata meta = new BlockMetadata("Posts", "List", null,
            Map.of("items", List.of(Map.of("id", 1, "title", "Hello"))));
        BlockProfile profile = new BlockProfile(meta, List.of(), StubBlock.class);

        String desc = AgentServiceUtils.describeState(profile);
        assertTrue(desc.contains("id=1"));
        assertTrue(desc.contains("title=Hello"));
    }

    @Test
    void describeState_with_no_metadata_returns_empty() {
        BlockProfile profile = new BlockProfile(null, List.of(), StubBlock.class);
        assertEquals("", AgentServiceUtils.describeState(profile));
    }

    // --- helpers ---

    private static JsonDataType.Object jsonObject(Map<String, JsonDataType> entries) {
        return new JsonDataType.Object(entries);
    }

    static abstract class StubBlock extends Block<Object, Object> {
        @Override public rsp.component.Lookup lookup() { return null; }
        @Override public String title() { return "Stub"; }
    }
}
