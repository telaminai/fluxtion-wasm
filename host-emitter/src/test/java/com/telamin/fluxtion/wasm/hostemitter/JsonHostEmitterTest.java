package com.telamin.fluxtion.wasm.hostemitter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden test for {@link JsonHostEmitter}: the emitted {@code JsonHost.java} for the
 * capabilities SEP must match the proven, hand-written shell in
 * {@code examples/capabilities} line-for-line. That hand-written file is the acceptance
 * bar from the P5 design — if the emitter and the golden ever drift, this fails.
 */
class JsonHostEmitterTest {

    /** The capabilities SEP — the parameters that produce the committed golden host. */
    private static final JsonHostEmitter.Spec CAPABILITIES = new JsonHostEmitter.Spec(
            "com.telamin.fluxtion.wasm.cap.host",
            "com.telamin.fluxtion.wasm.cap.generated.CapabilitiesProcessor",
            "JsonHost");

    /** Module basedir is host-emitter/; the golden lives in the sibling example module. */
    private static final Path GOLDEN = Path.of(
            "..", "examples", "capabilities", "src", "main", "java",
            "com", "telamin", "fluxtion", "wasm", "cap", "host", "JsonHost.java");

    @Test
    void emitsHostByteIdenticalToHandWrittenGolden() throws IOException {
        assumeTrue(Files.exists(GOLDEN),
                "golden not found at " + GOLDEN.toAbsolutePath() + " (run from the reactor)");

        String golden = Files.readString(GOLDEN, StandardCharsets.UTF_8);
        String emitted = new JsonHostEmitter().emit(CAPABILITIES).get("JsonHost.java");

        assertEquals(normalize(golden), normalize(emitted),
                "emitted JsonHost.java drifted from the hand-written capabilities golden");
    }

    @Test
    void emitsExportClassesMainForTheHost() {
        Map<String, String> files = new JsonHostEmitter().emit(CAPABILITIES);

        assertTrue(files.containsKey("JsonHostMain.java"), "Main file emitted");
        String main = files.get("JsonHostMain.java");
        assertTrue(main.contains("@JSExportClasses({JsonHost.class})"), "exports the host");
        assertTrue(main.contains("public class JsonHostMain"), "names the Main class off the host");
        assertTrue(main.contains("package com.telamin.fluxtion.wasm.cap.host;"), "in the host package");
    }

    @Test
    void parameterizesPackageProcessorAndHostName() {
        JsonHostEmitter.Spec spec = new JsonHostEmitter.Spec(
                "com.acme.edge", "com.acme.edge.generated.PricerProcessor", "PricerHost");
        String host = new JsonHostEmitter().emit(spec).get("PricerHost.java");

        assertTrue(host.contains("package com.acme.edge;"));
        assertTrue(host.contains("import com.acme.edge.generated.PricerProcessor;"));
        assertTrue(host.contains("public class PricerHost {"));
        assertTrue(host.contains("public PricerHost() {"));
        assertTrue(host.contains("PricerProcessor sep = new PricerProcessor();"));
        assertTrue(host.contains("proc.newHost('PricerHost')"), "javadoc uses the host name");
    }

    @Test
    void defaultHostNameIsJsonHost() {
        JsonHostEmitter.Spec spec = new JsonHostEmitter.Spec(
                "com.acme", "com.acme.gen.P");
        assertEquals("JsonHost", spec.hostClassName());
    }

    @Test
    void rejectsBlankInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new JsonHostEmitter.Spec(" ", "com.acme.gen.P", "H"));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonHostEmitter.Spec("com.acme", "  ", "H"));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonHostEmitter.Spec("com.acme", "com.acme.gen.P", ""));
    }

    /** Compare structure, not editor whitespace: trim trailing space + trailing blank lines. */
    private static String normalize(String s) {
        String body = Arrays.stream(s.stripLeading().split("\n", -1))
                .map(line -> {
                    int end = line.length();
                    while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
                        end--;
                    }
                    return line.substring(0, end);
                })
                .collect(Collectors.joining("\n"));
        // drop trailing blank lines
        while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        return body;
    }
}
