package rsp.util;

import java.util.Objects;

public final class CommandLineArguments {

    private final String[] args;

    public CommandLineArguments(final String[] args) {
       this.args = Objects.requireNonNull(args);
    }

    public String getFlagValue(final char flagLetter, final String flagName, final String defaultValue) {
        Objects.requireNonNull(flagName);
        if (flagName.isBlank()) {
            throw new IllegalArgumentException("Flag name is blank");
        }
        final String flagLetterString = String.valueOf(flagLetter);
        for (int i = args.length - 1; i >= 0; i--) {
            if ((args[i].startsWith("-") &&  args[i].substring(1).equals(flagLetterString))
                    || (args[i].startsWith("--") &&  args[i].substring(2).equals(flagName))) {
                if (args.length > i + 1) {
                    return args[i + 1];
                } else {
                    return "";
                }
            }
        }
        return defaultValue;
    }

    public String getFlagValue(final char flagLetter, final String flagName) {
        final String result = getFlagValue(flagLetter, flagName, null);
        if (result == null) {
            throw new FlagNotFoundException("Flag -" + flagLetter + " --" + flagName + " is missing the command line arguments");
        }
        return result;
    }

    public static class FlagNotFoundException extends RuntimeException {
        public FlagNotFoundException() {
        }

        public FlagNotFoundException(final String message) {
            super(message);
        }

        public FlagNotFoundException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

}
