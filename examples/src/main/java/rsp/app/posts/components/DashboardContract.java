package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

public class DashboardContract extends ViewContract {

    public static final ContextKey.StringKey<DashboardModel> DASHBOARD_MODEL =
            new ContextKey.StringKey<>("dashboard.model", DashboardModel.class);

    public DashboardContract(final Lookup lookup) {
        super(lookup);
    }

    @Override
    public ComponentContext enrichContext(final ComponentContext context) {
        return context
                .with(ContextKeys.CONTRACT_CLASS, getClass())
                .with(ContextKeys.CONTRACT_TITLE, title())
                .with(DASHBOARD_MODEL, DashboardModel.demo());
    }

    @Override
    public String title() {
        return "Dashboard";
    }
}
