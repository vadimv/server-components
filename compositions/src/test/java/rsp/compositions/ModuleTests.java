package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Module interface default methods and configuration.
 */
public class ModuleTests {

    @Nested
    class EditModeConfigurationTests {

        @Test
        void default_edit_mode_is_separate_page() {
            final Module module = new TestModule();

            assertEquals(EditMode.SEPARATE_PAGE, module.editMode());
        }

        @Test
        void default_create_token_is_new() {
            final Module module = new TestModule();

            assertEquals("new", module.createToken());
        }

        @Test
        void edit_mode_can_be_overridden() {
            final Module module = new ModalModeModule();

            assertEquals(EditMode.MODAL, module.editMode());
        }

        @Test
        void create_token_can_be_overridden() {
            final Module module = new CustomTokenModule();

            assertEquals("_", module.createToken());
        }
    }

    @Nested
    class EditContractDiscoveryTests {

        @Test
        void edit_contract_class_finds_edit_view_contract_in_views() {
            final Module module = new ModuleWithEditView();

            final Class<? extends EditViewContract<?>> editClass = module.editContractClass();

            assertNotNull(editClass);
            assertEquals(TestEditContract.class, editClass);
        }

        @Test
        void edit_contract_class_returns_null_when_not_found() {
            final Module module = new ModuleWithoutEditView();

            final Class<? extends EditViewContract<?>> editClass = module.editContractClass();

            assertNull(editClass);
        }

        @Test
        void edit_contract_class_finds_first_edit_view_contract() {
            final Module module = new ModuleWithMultipleViews();

            final Class<? extends EditViewContract<?>> editClass = module.editContractClass();

            assertNotNull(editClass);
            // Should find the first EditViewContract in the list
            assertEquals(TestEditContract.class, editClass);
        }

        @Test
        void edit_contract_class_skips_non_edit_contracts() {
            final Module module = new ModuleWithMixedViews();

            final Class<? extends EditViewContract<?>> editClass = module.editContractClass();

            assertNotNull(editClass);
            assertEquals(TestEditContract.class, editClass);
        }
    }

    @Nested
    class ModuleInterfaceTests {

        @Test
        void views_returns_list() {
            final Module module = new TestModule();

            final List<ViewPlacement> views = module.views();

            assertNotNull(views);
        }
    }

    // Test fixtures

    static class TestModule implements Module {
        @Override
        public List<ViewPlacement> views() {
            return List.of();
        }
    }

    static class ModalModeModule extends TestModule {
        @Override
        public EditMode editMode() {
            return EditMode.MODAL;
        }
    }

    static class CustomTokenModule extends TestModule {
        @Override
        public String createToken() {
            return "_";
        }
    }

    static class ModuleWithEditView extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestEditContract.class, TestEditContract::new)
            );
        }
    }

    static class ModuleWithoutEditView extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new)
            );
        }
    }

    static class ModuleWithMultipleViews extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new),
                    new ViewPlacement(Slot.PRIMARY, TestEditContract.class, TestEditContract::new),
                    new ViewPlacement(Slot.PRIMARY, AnotherEditContract.class, AnotherEditContract::new)
            );
        }
    }

    static class ModuleWithMixedViews extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new),
                    new ViewPlacement(Slot.PRIMARY, TestViewContract.class, TestViewContract::new),
                    new ViewPlacement(Slot.PRIMARY, TestEditContract.class, TestEditContract::new)
            );
        }
    }

    // Test contract classes

    static class TestViewContract extends ViewContract {
        TestViewContract(final ComponentContext context) {
            super(context);
        }
    }

    static class TestListContract extends ListViewContract<Object> {
        TestListContract(final ComponentContext context) {
            super(context);
        }

        @Override
        public int page() {
            return 1;
        }

        @Override
        public String sort() {
            return "asc";
        }

        @Override
        public List<Object> items() {
            return List.of();
        }
    }

    record TestEntity(String id, String name) {}

    static class TestEditContract extends EditViewContract<TestEntity> {
        TestEditContract(final ComponentContext context) {
            super(context);
        }

        @Override
        protected String resolveId() {
            return null;
        }

        @Override
        public TestEntity item() {
            return null;
        }

        @Override
        public ListSchema schema() {
            return ListSchema.fromRecordClass(TestEntity.class);
        }

        @Override
        public boolean save(final Map<String, Object> fieldValues) {
            return true;
        }
    }

    static class AnotherEditContract extends EditViewContract<TestEntity> {
        AnotherEditContract(final ComponentContext context) {
            super(context);
        }

        @Override
        protected String resolveId() {
            return null;
        }

        @Override
        public TestEntity item() {
            return null;
        }

        @Override
        public ListSchema schema() {
            return ListSchema.fromRecordClass(TestEntity.class);
        }

        @Override
        public boolean save(final Map<String, Object> fieldValues) {
            return true;
        }
    }
}
