package rsp.examples.components.entities;

import java.util.Arrays;
import java.util.List;

public class AuthorsBooksSeviceStubInit {
    private final static String[] firstNames = new String[] {"Joe", "Mary"};
    private final static String[] secondNames = new String[] {"Doe", "Smith"};
    public static AuthorsBooksService init(AuthorsBooksService authorsBooksService) {
        var authorsNames = Arrays.stream(firstNames).flatMap(fn -> Arrays.stream(secondNames).map(sn -> new Name(fn, sn)));
        authorsBooksService.createAuthor(new Name("Joe", "Doe"));
                        //   .thenApply(s -> authorsBooksService.) ;

        return authorsBooksService;
    }
}
