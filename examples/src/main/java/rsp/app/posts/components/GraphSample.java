package rsp.app.posts.components;

public record GraphSample(String label, int value) {
    public GraphSample {
        label = label == null ? "" : label;
    }
}
