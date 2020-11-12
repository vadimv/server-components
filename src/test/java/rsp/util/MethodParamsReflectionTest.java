package rsp.util;

import org.junit.Assert;
import org.junit.Test;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;

public class MethodParamsReflectionTest {

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {
        Objenesis objenesis = new ObjenesisStd();
        TestEntity obj = objenesis.newInstance(TestEntity.class);

        Field declaredField = TestEntity.class.getDeclaredField("field1");

        declaredField.setAccessible(true);
        declaredField.set(obj, "new value");

        declaredField.setAccessible(false);
        Assert.assertEquals("new value", obj.field1);

    }

}
