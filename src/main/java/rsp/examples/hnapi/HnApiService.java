package rsp.examples.hnapi;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class HnApiService {
    private static final String HACKER_NEWS_BASE_URL = "https://hacker-news.firebaseio.com/v0/";
    public static final int PAGE_SIZE = 50;
    public static final ForkJoinPool EXECUTOR = new ForkJoinPool(PAGE_SIZE);

    public CompletableFuture<List<Integer>> storiesIds() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseStoriesIds(httpGet(new URL(HACKER_NEWS_BASE_URL + "topstories.json" )))
                                            .stream().collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    public CompletableFuture<State.Story> story(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final long startTime = System.currentTimeMillis();
                State.Story result =  parseStory(httpGet(new URL(HACKER_NEWS_BASE_URL + "item/" + id + ".json" )));
                System.out.println(System.currentTimeMillis() - startTime);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    public CompletableFuture<List<State.Story>> stories(List<Integer> storiesIds) {
        return sequence(storiesIds.stream().map(id -> story(id)).collect(Collectors.toList()));
    }

    private static<T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> listOfCompletableFutures) {
        return CompletableFuture.allOf(listOfCompletableFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> listOfCompletableFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private static State.Story parseStory(String storyJsonStr) throws ParseException {
        final JSONParser jsonParser = new JSONParser();
        final JSONObject storyJson = (JSONObject) jsonParser.parse(storyJsonStr);
        return new State.Story((long) storyJson.get("id"),
                               (String) storyJson.get("title"),
                               (String) storyJson.get("url"));
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
