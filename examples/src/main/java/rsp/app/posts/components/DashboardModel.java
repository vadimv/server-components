package rsp.app.posts.components;

import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogStreamService;

import java.util.List;

public record DashboardModel(DashboardLayout layout) {

    public DashboardModel {
        layout = layout == null ? DashboardDsl.dashboard().build() : layout;
    }

    public static DashboardModel demo() {
        List<GraphSample> commentsRateSamples = List.of(
                new GraphSample("09:00", 18),
                new GraphSample("10:00", 24),
                new GraphSample("11:00", 21),
                new GraphSample("12:00", 31),
                new GraphSample("13:00", 28),
                new GraphSample("14:00", 36),
                new GraphSample("13:00", 28)
        );

        return new DashboardModel(DashboardDsl.dashboard()
                .columns(12)
                .rowHeightPx(96)
                .gap("1rem")
                .place(new CommentsRateGraphWidget(commentsRateSamples),
                        DashboardDsl.at(1, 1).span(6, 3))
                .build());
    }

    public static DashboardModel live(final CommentRateStreamService commentRateStreamService,
                                      final LogStreamService logStreamService) {
        return new DashboardModel(DashboardDsl.dashboard()
                .columns(12)
                .rowHeightPx(96)
                .gap("1.5rem")
                .place(CommentsRateGraphWidget.live(commentRateStreamService),
                        DashboardDsl.at(1, 1).span(6, 3))
                .place(LogsWidget.live(logStreamService),
                        DashboardDsl.at(1, 4).span(12, 3))
                .build());
    }

    public record GraphSample(String label, int value) {
        public GraphSample {
            label = label == null ? "" : label;
        }
    }
}
