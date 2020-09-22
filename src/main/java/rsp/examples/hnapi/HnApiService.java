package rsp.examples.hnapi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HnApiService {
    private static final String HACKER_NEWS_BASE_URL = "https://hacker-news.firebaseio.com/v0/";

    private static final Pattern FIELD_REGEX = Pattern.compile("\"(.+?)\" *?: *?(\".+?\"|\\d+|\\[.+?\\])");
    public static ForkJoinPool EXECUTOR = new ForkJoinPool(30);

    public CompletableFuture<List<Integer>> storiesIds() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseStoriesIds(httpGet(new URL(HACKER_NEWS_BASE_URL + "topstories.json" )))
                        .stream().limit(30).collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    public CompletableFuture<State.Story> story(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseStory(httpGet(new URL(HACKER_NEWS_BASE_URL + "item/" + id + ".json" )));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    private static State.Story parseStory(String storyJson) {
        final Matcher matcher = FIELD_REGEX.matcher(storyJson);
        final Map<String, String> fields = new HashMap<>();
        while(matcher.find())
        {
            String name=matcher.group(1);
            String value=matcher.group(2);
            fields.put(name, value);
        }
        System.out.println("ID: " + fields.get("id") + " " + System.currentTimeMillis());
        return new State.Story(Integer.parseInt(fields.get("id")), fields.get("title"));
    }

    private static List<Integer> parseStoriesIds(String storiesIdsJsonArray) {
        return Arrays.stream(storiesIdsJsonArray.substring(1, storiesIdsJsonArray.length() - 1)
                                                             .split(","))
                                                             .map(s -> s.trim())
                                                             .map(Integer::parseInt)
                                                             .collect(Collectors.toList());
    }


    private static String httpGet(URL url) throws Exception {
        final StringBuilder result = new StringBuilder();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try(final BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
}
