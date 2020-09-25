package rsp.util;
import com.google.javascript.jscomp.*;
import com.google.javascript.jscomp.Compiler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JsUtils {
    public static void main(String[] args) throws IOException {
        assembleJs(new File(args[0]), new File(args[1]));
    }

    public static void assembleJs(File sourceDir, File targetDir) throws IOException {
        System.out.println("Assembling ES6 sources using Google Closure Compiler");
        if(!sourceDir.isDirectory()) {
            throw new IllegalStateException(sourceDir.getAbsolutePath() + " sources directory expected");
        }

        if(!targetDir.isDirectory()) {
            throw new IllegalStateException(sourceDir.getAbsolutePath() + " target directory expected");
        }

        final File sourceOutputFile = new File(targetDir, "korolev-client.min.js");
        final File sourceMapOutputFile = new File(targetDir, "korolev-client.min.js.map");

        final Compiler compiler = new Compiler();
        final List<SourceFile> externs = AbstractCommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER);
        final Result result = compiler.compile(externs, inputs(sourceDir), options(sourceDir, sourceMapOutputFile));
        final String compiledJs = compiler.toSource();
        final StringBuilder sourceMapStringBuilder = new StringBuilder();
        result.sourceMap.appendTo(sourceMapStringBuilder, sourceMapOutputFile.getName());

        Files.writeString(sourceOutputFile.toPath(),
                        "(function(){"
                        + compiledJs
                        + "}).call(this);\n//# sourceMappingURL=korolev-client.min.js.map\n");
        Files.writeString(sourceMapOutputFile.toPath(), sourceMapStringBuilder.toString());
    }

    private static List<SourceFile> inputs(File sourceDir) {
        return Arrays.stream(sourceDir.listFiles()).map(file -> {
                final String path = file.getAbsolutePath();
                final Charset charset = StandardCharsets.UTF_8;
                return SourceFile.fromFile(path, charset);
            }).collect(Collectors.toList());
    }

    private static CompilerOptions options(File source, File sourceMapOutputFile) {
            final CompilerOptions options = new CompilerOptions();
            options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
            options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT);
            options.setSourceMapIncludeSourcesContent(true);
            options.setSourceMapLocationMappings(List.of(new SourceMap.PrefixLocationMapping(source.getAbsolutePath(), "korolev/es6")));
            options.setSourceMapOutputPath(sourceMapOutputFile.getName());
            options.setEnvironment(CompilerOptions.Environment.BROWSER);

            CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
            return options;
    }
}
