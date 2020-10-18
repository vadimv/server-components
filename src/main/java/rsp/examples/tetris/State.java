package rsp.examples.tetris;

public class State {
    public final Stage stage;
    public State(Stage stage) {
        this.stage = stage;
    }

    public static State initialState() {
        return new State(Stage.create());
    }

    public State setTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new State(stage.setTetramino(tetramino, x, y));
    }

    public State addTetraminoToCells() {
        return new State(stage.addTetraminoToCells());
    }

    public boolean checkCollision(int dx, int dy, boolean rotate) {
        return stage.checkCollision(dx, dy, rotate);
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

    public State rotateTetramino() {
        return new State(stage.rotateCcw());
    }
}
