package rsp.app.posts.components;

import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogStreamService;
import rsp.compositions.dashboard.DashboardDsl;
import rsp.compositions.dashboard.DashboardModel;

import java.util.List;

/**
 * App-specific dashboard compositions for the posts example. Assembles concrete widgets
 * ({@link CommentsRateGraphWidget}, {@link LogsWidget}) into a {@link DashboardModel} using
 * the generic {@code rsp.compositions.dashboard} grid DSL.
 */
public final class DemoDashboards {

    private DemoDashboards() {}

    public static DashboardModel live(final CommentRateStreamService commentRateStreamService,
                                      final LogStreamService logStreamService) {
        return new DashboardModel(DashboardDsl.dashboard()
                .columns(12)
                .rowHeightPx(96)
                .gap("1.5rem")
                .place(CommentsRateGraphWidget.live(commentRateStreamService),
                        DashboardDsl.at(1, 1).span(6, 3))
                .place(LogsWidget.live(logStreamService),
                        DashboardDsl.at(1, 4).span(10, 3))
                .build());
    }
}
