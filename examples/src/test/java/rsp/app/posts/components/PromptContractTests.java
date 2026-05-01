package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.PostService;
import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AllowAllSpawner;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.Scene;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultListView;
import rsp.page.QualifiedSessionId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptContractTests {

    @Test
    void enrichContext_derives_active_category_from_scene_structure() {
        final PostService postService = new PostService();
        final Group mainContracts = new Group("Admin")
                .add(new Group("Posts")
                        .bind(PostsListContract.class,
                              lookup -> new PostsListContract(lookup, postService),
                              DefaultListView::new));
        final Composition composition = new Composition(
                new Router().route("/posts", PostsListContract.class),
                new DefaultLayout(),
                mainContracts);
        final Lookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class, new QualifiedSessionId("device", "session"));
        final PostsListContract routedContract = new PostsListContract(lookup, postService);
        final Scene scene = Scene.of(routedContract, Map.of(), Map.of(), composition);
        final PromptContract promptContract = new PromptContract(
                lookup,
                new PromptService(),
                new AgentService(),
                new ActionDispatcher(),
                new Authorization(_ -> new AccessDecision.Allow(), Attributes.empty()),
                new AllowAllSpawner(),
                mainContracts.structureTree());

        final ComponentContext enriched = promptContract.enrichContext(
                new ComponentContext().with(ContextKeys.SCENE, scene));

        assertEquals("Posts", enriched.get(PromptContextKeys.ACTIVE_CATEGORY));
    }
}
