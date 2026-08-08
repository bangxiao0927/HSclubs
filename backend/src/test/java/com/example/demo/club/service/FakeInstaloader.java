package com.example.demo.club.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * A stand-in for the Instaloader command line, used by {@link InstagramAvatarCacheServiceTest}.
 *
 * <p>These tests need a program that prints a URL, sleeps, floods its output, fails, or records
 * the arguments it was handed. They used to be POSIX shell scripts, which meant the whole class
 * failed on Windows (the executable bit cannot be set, and {@code ProcessBuilder} will not run a
 * {@code .sh}), so a contributor there could never get a green suite and had to remember which
 * failures were "expected" -- see issue #109. The behaviour lives in this class instead, run by
 * the same JVM the tests run in, so it is identical on every platform and is exercised by CI.
 *
 * <p>{@link Script} writes the tiny platform launcher (a {@code .sh} or a {@code .cmd}) that the
 * service is pointed at as its {@code python-command}.
 */
public final class FakeInstaloader {

    private static final String SLEEP_MS = "sleepMs";
    private static final String EXIT_CODE = "exitCode";
    private static final String NOISE_LINES = "noiseLines";
    private static final String STDOUT_PREFIX = "stdout.";
    private static final String APPEND_FILE = "appendFile";
    private static final String APPEND_PREFIX = "append.";

    private FakeInstaloader() {
    }

    /**
     * @param args {@code [0]} is the config file written by {@link Script}; the rest are exactly
     *             what the service passes to the interpreter, so {@code args[1]} is {@code $1}
     *             in the shell scripts these replaced
     */
    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            config.load(in);
        }
        List<String> serviceArgs = List.of(args).subList(1, args.length);

        exitWhenTheLauncherDies();

        recordArguments(config, serviceArgs);

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int noiseLines = Integer.parseInt(config.getProperty(NOISE_LINES, "0"));
        for (int i = 0; i < noiseLines; i++) {
            out.println("WARNING noisy line " + i + " " + "x".repeat(80));
        }

        long sleepMs = Long.parseLong(config.getProperty(SLEEP_MS, "0"));
        if (sleepMs > 0) {
            Thread.sleep(sleepMs);
        }

        for (String line : orderedValues(config, STDOUT_PREFIX)) {
            out.println(line);
        }
        out.flush();

        System.exit(Integer.parseInt(config.getProperty(EXIT_CODE, "0")));
    }

    /**
     * Ends this process as soon as the launcher that started it is gone.
     *
     * <p>On POSIX the launcher {@code exec}s this JVM, so the service's {@code destroyForcibly()}
     * on a timed-out fetch kills it directly. Windows has no {@code exec}: the service kills
     * {@code cmd.exe} and this JVM would keep running (and keep the inherited output pipe open)
     * until its configured sleep finished, leaking a process per timeout test on the very
     * platform this fake exists to support.
     */
    private static void exitWhenTheLauncherDies() {
        ProcessHandle.current().parent().ifPresent(parent -> {
            Thread watchdog = new Thread(() -> {
                parent.onExit().join();
                Runtime.getRuntime().halt(143);
            }, "fake-instaloader-parent-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        });
    }

    /**
     * Appends one line per configured entry to the file the test named: {@code literal:<text>}
     * writes the text, {@code arg:<n>} writes the nth interpreter argument (1-based, matching
     * the {@code $n} the shell fixtures used).
     */
    private static void recordArguments(Properties config, List<String> serviceArgs) throws IOException {
        String appendFile = config.getProperty(APPEND_FILE);
        if (appendFile == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String entry : orderedValues(config, APPEND_PREFIX)) {
            if (entry.startsWith("literal:")) {
                lines.add(entry.substring("literal:".length()));
            } else if (entry.startsWith("arg:")) {
                int index = Integer.parseInt(entry.substring("arg:".length())) - 1;
                lines.add(index >= 0 && index < serviceArgs.size() ? serviceArgs.get(index) : "");
            }
        }
        Files.write(Path.of(appendFile), lines, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static List<String> orderedValues(Properties config, String prefix) {
        List<String> values = new ArrayList<>();
        for (int i = 0; config.containsKey(prefix + i); i++) {
            values.add(config.getProperty(prefix + i));
        }
        return values;
    }

    /** Builds the launcher the service is pointed at, plus the config that drives it. */
    public static final class Script {

        private final Path directory;
        private final String name;
        private final Properties config = new Properties();
        private int stdoutCount;
        private int appendCount;

        private Script(Path directory, String name) {
            this.directory = directory;
            this.name = name;
        }

        public static Script named(Path directory, String name) {
            return new Script(directory, name);
        }

        public Script prints(String line) {
            config.setProperty(STDOUT_PREFIX + stdoutCount++, line);
            return this;
        }

        /** Prints this many long lines before anything else, to flood the output pipe. */
        public Script printsNoiseLines(int lines) {
            config.setProperty(NOISE_LINES, Integer.toString(lines));
            return this;
        }

        public Script sleeps(long millis) {
            config.setProperty(SLEEP_MS, Long.toString(millis));
            return this;
        }

        public Script exitsWith(int exitCode) {
            config.setProperty(EXIT_CODE, Integer.toString(exitCode));
            return this;
        }

        public Script recordsInto(Path file) {
            config.setProperty(APPEND_FILE, file.toAbsolutePath().toString());
            return this;
        }

        /** Records a fixed marker per invocation, for tests that only count calls. */
        public Script recordsMarker() {
            config.setProperty(APPEND_PREFIX + appendCount++, "literal:x");
            return this;
        }

        /** Records the nth interpreter argument (1-based), e.g. 3 for the club handle. */
        public Script recordsArgument(int oneBasedIndex) {
            config.setProperty(APPEND_PREFIX + appendCount++, "arg:" + oneBasedIndex);
            return this;
        }

        /** @return the path to hand the service as its {@code python-command} */
        public String create() throws IOException {
            Path configFile = directory.resolve(name + ".properties");
            try (var out = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                config.store(out, "FakeInstaloader configuration for " + name);
            }

            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("java.class.path");
            String mainClass = FakeInstaloader.class.getName();
            boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

            Path launcher = directory.resolve(name + (windows ? ".cmd" : ".sh"));
            if (windows) {
                Files.writeString(launcher,
                    "@echo off\r\n\"%s\" -cp \"%s\" %s \"%s\" %%*\r\n"
                        .formatted(java, classpath, mainClass, configFile),
                    StandardCharsets.UTF_8);
            } else {
                Files.writeString(launcher,
                    "#!/bin/sh\nexec \"%s\" -cp \"%s\" %s \"%s\" \"$@\"\n"
                        .formatted(java, classpath, mainClass, configFile),
                    StandardCharsets.UTF_8);
                if (!launcher.toFile().setExecutable(true)) {
                    throw new IOException("Could not make the fake interpreter executable: " + launcher);
                }
            }
            return launcher.toString();
        }
    }
}
