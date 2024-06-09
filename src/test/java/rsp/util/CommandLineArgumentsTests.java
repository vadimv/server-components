package rsp.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CommandLineArgumentsTests {

    @Test
    void gets_value_for_a_flag() {
        final String[] args = {"first", "second", "-A", "value-a1", "third" };
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("value-a1", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_a_flag_at_end_position() {
        final String[] args = {"first", "second", "-A", "value-a1"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("value-a1", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_a_flag_by_name() {
        final String[] args = {"first", "second", "--abc", "value-a1"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("value-a1", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_a_flag_with_empty_value() {
        final String[] args = {"first", "second", "-A"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_a_flag_with_empty_value_by_name() {
        final String[] args = {"first", "second", "--abc"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_a_flag_with_last_position_precedence() {
        final String[] args = {"first", "second", "-A", "value-a1", "-A", "value-a2"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("value-a2", commandLineArguments.getFlagValue('A', "abc"));
    }

    @Test
    void gets_value_for_an_absent_flag_with_default_value_provided() {
        final String[] args = {"first", "second", "-A", "value-a1", "-A", "value-a2"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertEquals("default-value", commandLineArguments.getFlagValue('x', "xyz", "default-value"));
    }

    @Test
    void throws_exception_when_flag_is_absent() {
        final String[] args = {"first", "second", "-A", "value-a1", "-A", "value-a2"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertThrows(CommandLineArguments.FlagNotFoundException.class, () -> {
            commandLineArguments.getFlagValue('k', "klm");
        });
    }

    @Test
    void throws_exception_for_an_empty_flag_name_method_argument() {
        final String[] args = {"first", "second", "-A", "value-a1", "-A", "value-a2"};
        final CommandLineArguments commandLineArguments = new CommandLineArguments(args);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            commandLineArguments.getFlagValue('e', "");
        });
    }
}
