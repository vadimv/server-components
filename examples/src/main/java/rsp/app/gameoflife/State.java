package rsp.app.gameoflife;


public class State {
    public final Board board;
    public final boolean isRunning;

    public State(Board board, boolean isRunning) {
        this.board = board;
        this.isRunning = isRunning;
    }

    public static State initialState() {
        return initialState(false);
    }

    public static State initialState(boolean random) {
        return new State(Board.create(random), false);
    }

    public State toggleCell(int x, int y) {
        return new State(board.toggle(x, y), isRunning);
    }

    public State advance() {
        return new State(board.advance(), isRunning);
    }

    public State setIsRunning(boolean isRunning) {
        return new State(board, isRunning);
    }
}
