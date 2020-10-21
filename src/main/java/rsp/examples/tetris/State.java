package rsp.examples.tetris;

import java.util.Objects;
import java.util.Optional;

public class State {
    public final Stage stage;
    public final boolean isRunning;
    public State(Stage stage, boolean isRunning) {
        this.stage = Objects.requireNonNull(stage);
        this.isRunning = isRunning;
    }

    public static State initialState() {
        return new State(Stage.create(), false);
    }

    public State start() {
        return new State(stage, true);
    }

    public State stop() {
        return new State(stage, false);
    }

    public Optional<State> newTetramino() {
        final Tetromions.Tetromino t = Tetromions.randomTetromino();
        final State newState = addTetraminoToCells()
                .collapseFullLayers()
                .setTetramino(t, (Stage.WIDTH - t.shape.length) / 2, 0);

        return !newState.checkCollision(0, 0, false) ? Optional.of(newState) : Optional.empty();
    }

    public Optional<State> tryMoveLeft() {
        return !checkCollision(-1, 0, false) ? Optional.of(moveTetraminoLeft()) : Optional.empty();
    }

    public Optional<State> tryMoveRight() {
        return !checkCollision(1, 0, false) ? Optional.of(moveTetraminoRight()) : Optional.empty();
    }

    public Optional<State> tryMoveDown() {
        return !checkCollision(0, 1, false) ? Optional.of(moveTetraminoDown()) : Optional.empty();
    }

    public Optional<State> tryRotate() {
        return !checkCollision(0, 0, true) ? Optional.of(rotateTetramino()) : Optional.empty();
    }

    public int score() {
        return stage.collapsedLayersCount * 10;
    }

    private State setTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new State(stage.setTetramino(tetramino, x, y), isRunning);
    }

    private State addTetraminoToCells() {
        return new State(stage.addTetraminoToCells(), isRunning);
    }

    private State collapseFullLayers() {
        return new State(stage.collapseFullLayers(), isRunning);
    }

    private boolean checkCollision(int dx, int dy, boolean rotate) {
        return stage.checkCollision(dx, dy, rotate);
    }

    private State moveTetraminoDown() {
        return new State(stage.moveTetraminoDown(), isRunning);
    }

    private State moveTetraminoLeft() {
        return new State(stage.moveTetraminoLeft(), isRunning);
    }

    private State moveTetraminoRight() {
        return new State(stage.moveTetraminoRight(), isRunning);
    }

    private State rotateTetramino() {
        return new State(stage.rotateCcw(), isRunning);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return stage.equals(state.stage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stage);
    }
}
