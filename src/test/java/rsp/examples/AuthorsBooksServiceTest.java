package rsp.examples;

import org.junit.Assert;
import org.junit.Test;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.SimpleAuthorsEntityService;
import rsp.examples.crud.entities.SimpleBooksEntityService;

public class AuthorsBooksServiceTest {
    @Test
    public void test() {
        final SimpleAuthorsEntityService authorsService = new SimpleAuthorsEntityService();
        final SimpleBooksEntityService booksService = new SimpleBooksEntityService();
        AuthorsBooksServiceStubInit.init(authorsService, booksService);

        Assert.assertNotNull(booksService.getOne(1L));
    }
}
