package rsp.examples.tetris;

public class State {
    public final Stage stage;
    public State(Stage stage) {
        this.stage = stage;
    }

    public static State initialState() {
        return new State(Stage.create());
    }

    public State addTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new State(stage.addTetramino(tetramino, x, y));
    }

    public State moveTetraminoDown() {
        return new State(stage.moveTetraminoDown());
    }

    public State moveTetraminoLeft() {
        return new State(stage.moveTetraminoLeft());
    }

    public State moveTetraminoRight() {
        return new State(stage.moveTetraminoRight());
    }
}
