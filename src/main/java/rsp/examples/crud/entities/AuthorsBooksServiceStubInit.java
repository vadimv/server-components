package rsp.examples.crud.entities;

import rsp.examples.crud.services.EntityService;
import rsp.util.StreamUtils;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class AuthorsBooksServiceStubInit {
    private final static int AUTHORS_GROUP_SIZE = 3;
    private final static String[] firstNames = new String[] {"Joe", "Mary", "Nick", "Steven"};
    private final static String[] secondNames = new String[] {"Doe", "Smith", "Tailor"};

    private final static String[] bookTitleAdjectives = new String[] { "Funny", "Simple", "Advanced", "Intermediate"};
    private final static String[] bookTitleSubjects = new String[] { "Music", "Guitar", "Computers", "Science", "Cooking"};

    /**
     * A book could be written either by a single author or by a group of authors
     */
    public static void init(EntityService<String, Author> authorsService, EntityService<String, Book> booksService) {
        final var authorsNames = Arrays.stream(firstNames).flatMap(fn ->
                Arrays.stream(secondNames).map(sn -> new Name(fn, sn)));
        final var booksTitles = Arrays.stream(bookTitleAdjectives).flatMap(adj ->
                Arrays.stream(bookTitleSubjects).map(subj -> adj + " " + subj));

        // Add authors to the repository
        final var createdAuthors =  authorsNames.map(name -> authorsService.create(new Author(name)))
                                                                           .collect(Collectors.toList());

        StreamUtils.sequence(createdAuthors).thenAccept(authorsList -> {
            final List<Long> authorsIds = authorsList.stream().map(a -> Long.valueOf(a.get().key)).collect(Collectors.toList());

            //The infinite stream of authors ids with either 1 or AUTHORS_GROUP_SIZE authors of for a book
            final Stream<List<Long>> groupedAuthorsIds = Stream.iterate(new Tuple2<>(0, List.<Long>of()),
                    n -> n._1 % AUTHORS_GROUP_SIZE == 0 ?
                        new Tuple2<>(n._1 + 1, List.of(Long.valueOf(authorsIds.get(n._1 % authorsIds.size()))))
                      : new Tuple2<>(n._1 + AUTHORS_GROUP_SIZE - 1,
                                     LongStream.range(0, AUTHORS_GROUP_SIZE).map(j ->
                                             authorsIds.get(Long.valueOf((n._1 + j) % authorsIds.size()).intValue())).boxed().collect(Collectors.toList())))
                       .map(t -> t._2).skip(1);

            // Add books to the repository
            StreamUtils.zip(booksTitles, groupedAuthorsIds, (title, ids) -> new Tuple2<>(title, ids)).forEach(i -> {
                StreamUtils.sequence(i._2.stream().map(aId -> authorsService.getOne(aId.toString())).collect(Collectors.toList()))
                        .thenAccept(a -> {
                            final Set<KeyedEntity<String,Author>> bookAuthors = a.stream().filter(opt -> opt.isPresent()).map(opt -> opt.get()).collect(Collectors.toSet());
                            final var book = new Book(i._1, "desc", bookAuthors);
                            booksService.create(book);

                        });

            });
        });

    }
}
