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

/**
 * The Closure Compiler runner for packaging and minimizing JavaScript code.
 */
public final class JsCompiler {
    private static final System.Logger logger = System.getLogger(JsCompiler.class.getName());

    public static void main(final String[] args) throws IOException {
        if (!assembleJs(new File(args[0]), new File(args[1]), args[2]).errors.isEmpty()) {
            System.exit(1);
        }
    }

    public static Result assembleJs(final File sourceDir, final File targetDir, final String baseName) throws IOException {
        logger.log( System.Logger.Level.INFO, "Assembling ES6 sources using Google Closure Compiler");

        if (!sourceDir.isDirectory()) {
            throw new IllegalStateException(sourceDir.getAbsolutePath() + " sources directory expected");
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        final File sourceOutputFile = new File(targetDir, baseName + ".min.js");
        final File sourceMapOutputFile = new File(targetDir, baseName + ".min.js.map");

        final Compiler compiler = new Compiler();
        final List<SourceFile> externs = AbstractCommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER);
        final Result result = compiler.compile(externs, inputs(sourceDir), options(sourceDir, sourceMapOutputFile));
        final String compiledJs = compiler.toSource();
        final StringBuilder sourceMapStringBuilder = new StringBuilder();
        result.sourceMap.appendTo(sourceMapStringBuilder, sourceMapOutputFile.getName());

        Files.writeString(sourceOutputFile.toPath(),
                        "(function(){"
                        + compiledJs
                        + "}).call(this);\n//# sourceMappingURL=" + baseName + ".min.js.map\n");
        Files.writeString(sourceMapOutputFile.toPath(), sourceMapStringBuilder.toString());
        return result;
    }

    private static List<SourceFile> inputs(final File sourceDir) {
        return Arrays.stream(sourceDir.listFiles()).map(file -> {
                final String path = file.getAbsolutePath();
                final Charset charset = StandardCharsets.UTF_8;
                return SourceFile.fromFile(path, charset);
            }).toList();
    }

    private static CompilerOptions options(final File source, final File sourceMapOutputFile) {
        final CompilerOptions options = new CompilerOptions();
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT);
        options.setSourceMapIncludeSourcesContent(true);
        options.setSourceMapLocationMappings(List.of(new SourceMap.PrefixLocationMapping(source.getAbsolutePath(), "RSP/es6")));
        options.setSourceMapOutputPath(sourceMapOutputFile.getName());
        options.setEnvironment(CompilerOptions.Environment.BROWSER);

        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        return options;
    }
}
