package rsp.examples.tetris;

public class State {
    public final Stage stage;
    public State(Stage stage) {
        this.stage = stage;
    }

    public static State initialState() {
        return new State(Stage.create());
    }
}
