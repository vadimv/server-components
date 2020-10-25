package rsp.examples.hnapi;

public class State {

    public final int[] storiesIds;
    public final Story[] stories;
    public final int pageNum;
    public State(int[] storiesIds, Story[] stories, int pageNum) {
        this.storiesIds = storiesIds;
        this.stories = stories;
        this.pageNum = pageNum;
    }

    public static class Story {
        public final long id;
        public final String name;
        public final String url;

        public Story(long id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }
}
