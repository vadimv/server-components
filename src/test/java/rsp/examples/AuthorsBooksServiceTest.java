package rsp.examples;

import org.junit.Assert;
import org.junit.Test;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.SimpleAuthorsBooksService;

public class AuthorsBooksServiceTest {
    @Test
    public void test() {
        final SimpleAuthorsBooksService simpleAuthorsBooksService = new SimpleAuthorsBooksService();
        AuthorsBooksServiceStubInit.init(simpleAuthorsBooksService);

        Assert.assertNotNull(simpleAuthorsBooksService.getAuthor(1));
    }
}
