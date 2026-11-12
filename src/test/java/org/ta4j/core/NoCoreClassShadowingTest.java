package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.ATRIndicatorPlus;

/**
 * ta4j-extended shares the {@code org.ta4j.core} packages with ta4j-core. A class with the same fully qualified name as
 * a ta4j-core class shadows it depending on the classpath order, and code compiled against the core API then fails at
 * runtime with {@code NoSuchMethodError} or silently gets different values.
 */
public class NoCoreClassShadowingTest {

    @Test
    public void noMainClassHasTheNameOfATa4jCoreClass() throws IOException, URISyntaxException {
        Path classesDir = Path.of(ATRIndicatorPlus.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassLoader classLoader = ATRIndicatorPlus.class.getClassLoader();

        List<String> classFiles;
        try (Stream<Path> files = Files.walk(classesDir)) {
            classFiles = files.filter(file -> file.toString().endsWith(".class"))
                    .map(file -> classesDir.relativize(file).toString().replace('\\', '/'))
                    .toList();
        }
        assertTrue(classFiles.size() > 10, "no main classes found in " + classesDir);

        List<String> shadowing = classFiles.stream().filter(classFile -> {
            try {
                List<URL> copies = Collections.list(classLoader.getResources(classFile));
                return copies.size() > 1;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).toList();

        assertEquals(List.of(), shadowing, "classes that also exist in ta4j-core");
    }
}
