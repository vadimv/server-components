package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceUtilsTests {

    private static final EventKey.VoidKey CREATE_KEY = new EventKey.VoidKey("test.create");
    private static final EventKey.SimpleKey<Integer> PAGE_KEY =
        new EventKey.SimpleKey<>("test.page", Integer.class);

    private static final List<AgentAction> ACTIONS = List.of(
        new AgentAction("create", CREATE_KEY, "Create item"),
        new AgentAction("page", PAGE_KEY, "Go to page",
            new PayloadSchema.IntegerValue("page number"))
    );

    private static final StructureNode TREE = new StructureNode("Root", null,
        List.of(new StructureNode("Posts", null, List.of(), List.of(StubContract.class))),
        List.of());

    // --- buildToolDefinitions ---

    @Test
    void buildToolDefinitions_includes_actions_and_builtins() {
        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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
        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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
        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        AgentAction found = AgentServiceUtils.findAction("page", profile);

        assertNotNull(found);
        assertEquals("page", found.action());
    }

    @Test
    void findAction_returns_null_for_unknown() {
        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        assertNull(AgentServiceUtils.findAction("unknown", profile));
    }

    // --- resolveTargetContract ---

    @Test
    void resolveTargetContract_exact_match() {
        Class<? extends ViewContract> result =
            AgentServiceUtils.resolveTargetContract("StubContract", TREE);
        assertEquals(StubContract.class, result);
    }

    @Test
    void resolveTargetContract_fuzzy_match() {
        Class<? extends ViewContract> result =
            AgentServiceUtils.resolveTargetContract("Stub", TREE);
        assertEquals(StubContract.class, result);
    }

    @Test
    void resolveTargetContract_label_match() {
        Class<? extends ViewContract> result =
            AgentServiceUtils.resolveTargetContract("Posts", TREE);
        assertEquals(StubContract.class, result);
    }

    @Test
    void resolveTargetContract_returns_null_for_blank() {
        assertNull(AgentServiceUtils.resolveTargetContract("", TREE));
        assertNull(AgentServiceUtils.resolveTargetContract(null, TREE));
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

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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
            "payload", new JsonDataType.Number(3)));

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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
            "targetContract", new JsonDataType.String("StubContract")));

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        Optional<AgentResult> result = AgentServiceUtils.toAgentResult(output, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.NavigateResult.class, result.get());
        assertEquals(StubContract.class,
            ((AgentResult.NavigateResult) result.get()).targetContract());
    }

    // --- toolUseToAgentResult ---

    @Test
    void toolUseToAgentResult_plan_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "steps", new JsonDataType.Array(
                new JsonDataType.String("open page 2"),
                new JsonDataType.String("select all")),
            "message", new JsonDataType.String("Will do")));

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
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

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("text_reply", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.TextReply.class, result.get());
        assertEquals("Hi there!", ((AgentResult.TextReply) result.get()).message());
    }

    @Test
    void toolUseToAgentResult_navigate_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "targetContract", new JsonDataType.String("StubContract")));

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("navigate", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.NavigateResult.class, result.get());
        assertEquals(StubContract.class, ((AgentResult.NavigateResult) result.get()).targetContract());
    }

    @Test
    void toolUseToAgentResult_action_tool() {
        JsonDataType.Object input = jsonObject(Map.of(
            "payload", new JsonDataType.Number(3)));

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("page", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.ActionResult.class, result.get());
        assertEquals("page", ((AgentResult.ActionResult) result.get()).action().action());
    }

    @Test
    void toolUseToAgentResult_unknown_tool_returns_text_reply() {
        JsonDataType.Object input = jsonObject(Map.of());

        ContractProfile profile = new ContractProfile(null, ACTIONS, StubContract.class);
        Optional<AgentResult> result = AgentServiceUtils.toolUseToAgentResult("unknown", input, profile, TREE);

        assertTrue(result.isPresent());
        assertInstanceOf(AgentResult.TextReply.class, result.get());
    }

    // --- describeState ---

    @Test
    void describeState_with_items() {
        ContractMetadata meta = new ContractMetadata("Posts", "List", null,
            Map.of("items", List.of(Map.of("id", 1, "title", "Hello"))));
        ContractProfile profile = new ContractProfile(meta, List.of(), StubContract.class);

        String desc = AgentServiceUtils.describeState(profile);
        assertTrue(desc.contains("id=1"));
        assertTrue(desc.contains("title=Hello"));
    }

    @Test
    void describeState_with_no_metadata_returns_empty() {
        ContractProfile profile = new ContractProfile(null, List.of(), StubContract.class);
        assertEquals("", AgentServiceUtils.describeState(profile));
    }

    // --- helpers ---

    private static JsonDataType.Object jsonObject(Map<String, JsonDataType> entries) {
        return new JsonDataType.Object(entries);
    }

    static abstract class StubContract extends ViewContract {
        StubContract() { super(null); }
    }
}
