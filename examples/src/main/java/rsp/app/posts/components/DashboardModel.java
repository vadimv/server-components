package rsp.app.posts.components;

import java.util.List;

public record DashboardModel(List<GraphSample> commentsRateSamples) {

    public DashboardModel {
        commentsRateSamples = commentsRateSamples == null ? List.of() : List.copyOf(commentsRateSamples);
    }

    public static DashboardModel demo() {
        return new DashboardModel(List.of(
                new GraphSample("09:00", 18),
                new GraphSample("10:00", 24),
                new GraphSample("11:00", 21),
                new GraphSample("12:00", 31),
                new GraphSample("13:00", 28),
                new GraphSample("14:00", 36),
                new GraphSample("13:00", 28)
        ));
    }

    public record GraphSample(String label, int value) {
        public GraphSample {
            label = label == null ? "" : label;
        }
    }
}
