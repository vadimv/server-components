package rsp.util;

import org.junit.Assert;
import org.junit.Test;

public class EitherTests {

    @Test
    public void should_create_right_either() {
        final Either<Integer, String> e = Either.right("foo");

        e.on(s -> Assert.fail(),
                s -> Assert.assertEquals("foo", s));
    }

    @Test
    public void should_create_left_either() {
        final Either<Integer, String> e = Either.left(10);

        e.on(s -> Assert.assertTrue(10 == s),
             s -> Assert.fail());
    }
}
