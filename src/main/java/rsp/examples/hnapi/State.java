package rsp.examples.hnapi;

public class State {

    public final Story[] strories;

    public State(Story[] strories) {
        this.strories = strories;
    }

    public static class Story {
        public final int id;
        public final String name;

        public Story(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
