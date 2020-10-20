/*
 * This file is part of Antea IFC Export.
 *
 * Author: Federico Russo
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Antea srl
 * Modifications Copyright (c) Giovanni Velludo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tech.antea.ifc;

import buildingsmart.ifc.IfcProject;
import buildingsmart.util.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.EywaRoot;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * <p>This test verifies the conversion of Eywa Models. It is a {@code
 * Parametrized} test: the method annotated {@code @Parameters} creates an
 * {@code Iterable} of {@code Pair}s of {@code URL}s. This test gets
 * instantiated once for every pair of URLs using the constructor {@link
 * EywaToIfcConverterTest#EywaToIfcConverterTest(Pair)} (Pair)} that is provided
 * by the {@code
 * @RequiredArgsConstructor} annotation. Then, the {@code @Test} methods are
 * executed.</p>
 * <p>In order for tests to work, for each .eywa file in the test
 * resources there must be a corresponding expected .ifc file in the test
 * resources.</p>
 */
@RequiredArgsConstructor
@RunWith(value = Parameterized.class)
public class EywaToIfcConverterTest {
    public static final String EYWA_RESOURCES_PACKAGE = "tech.antea.models.cluster";
    private static final String IFC_PACKAGE = "buildingsmart.ifc";
    private static final String EYWA_EXTENSION = "eywa";
    private static final String IFC_EXTENSION = "ifc";
    private static final char S = java.io.File.separatorChar;
    private static final String INPUT_DIR = "tech" + S + "antea" + S + "models" + S + "cluster" + S;
    private static final String EXPECTED_OUTPUT_DIR = "tech" + S + "antea" + S + "expectedconversions" + S;
    private static final String OUTPUT_DIR = "." + S + "ifc-out" + S;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final EywaReader reader = new EywaReader(new EywaToIfcConverter());
    private static final String[] ifcRootSubclassesRegex;

    static {
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().whitelistPackages(IFC_PACKAGE).scan()) {
            ifcRootSubclassesRegex = scanResult.getSubclasses(IFC_PACKAGE + ".IfcRoot")
                    .filter(info -> !info.isAbstract())
                    .stream().map(info -> "^#[0-9]+=" + info.getSimpleName().toUpperCase() + "\\(.*")
                    .toArray(String[]::new);
        }
    }

    private final Pair<URL, URL> inputAndExpectedOutput;

    /**
     * This method finds all the "*.eywa" and "*.ifc" resources inside the
     * {@code tech.antea} package. It uses the ClassGraph library to do the
     * reflection and the classpath scanning. The .eywa file will be the left
     * element of the pair, the .ifc file will be the right element.
     */
    @Parameters(name = "{0}")
    public static Iterable<Pair<URL, URL>> data() {
        List<URL> eywaUrls;
        try (ScanResult r = new ClassGraph().whitelistPackages(EYWA_RESOURCES_PACKAGE).scan()) {
            eywaUrls = r.getResourcesWithExtension(EYWA_EXTENSION).getURLs();
        }
        List<URL> expectedIfcUrls = eywaUrls.stream().map(url -> {
            String inputPath = url.toString();
            String expectedOutputPath = inputPath.replace(INPUT_DIR, EXPECTED_OUTPUT_DIR);
            expectedOutputPath = expectedOutputPath.substring(0, expectedOutputPath.length() -
                    EYWA_EXTENSION.length()) + IFC_EXTENSION;
            URL expectedOutputURL = null;
            try {
                expectedOutputURL = new URL(expectedOutputPath);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return expectedOutputURL;
        }).collect(toList());
        return IntStream.range(0, eywaUrls.size())
                .mapToObj(i -> new Pair<>(eywaUrls.get(i), expectedIfcUrls.get(i)))
                .collect(toList());
    }

    /**
     * @param filePath The path to the IFC file of which to retrieve the DATA
     * section.
     * @return The DATA section of the IFC file.
     * @throws IOException If an I/O error occurs reading from the file or
     * a malformed or unmappable byte sequence is
     * read.
     * @throws SecurityException In the case of the default provider, and a
     * security manager is installed, the {@link
     * SecurityManager#checkRead(String) checkRead}
     * method is invoked to check read access to the
     * file.
     */
    private static String getDataSection(@NonNull String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.US_ASCII);
        lines = removeNonDeterministicOutput(lines);
        StringBuilder dataSection = new StringBuilder();
        for (int i = 6; i < lines.size() - 1; i++) {
            // DATA section starts at line 6 (if the first one is line 0) and
            // ends at the penultimate line
            dataSection.append(lines.get(i)).append("\n");
        }
        return dataSection.toString();
    }

    /**
     * Given a List of Strings representing the DATA section of an IFC file,
     * removes all the substrings that can vary between conversions of the same
     * Eywa file made in different moments. These are timestamps in
     * IFCOWNERHISTORY and GlobalIds in entities that are subtypes of IFCROOT.
     * @param strings The input List of Strings.
     * @return A copy of {@code strings} without non-deterministic substrings.
     * @throws NullPointerException If {@code strings} is {@code null}.
     */
    private static List<String> removeNonDeterministicOutput(@NonNull List<String> strings) {
        return strings.parallelStream().map(s -> {
            if (s.matches("^#[0-9]+=IFCOWNERHISTORY\\(.*")) {
                return s.replaceAll("[0-9]{10,}", "");
            }
            for (String regex : ifcRootSubclassesRegex) {
                if (s.matches(regex)) {
                    return s.replaceFirst(Matcher.quoteReplacement("'[0-9A-Za-z_$]+'"), "''");
                }
            }
            return s;
        }).collect(toList());
    }

    /**
     * Converts each Eywa file from the resources directory to an IFC file.
     */
    @Test
    public void testFile() throws IOException {
        URL inputURL = inputAndExpectedOutput.getLeft();
        URL expectedOutputURL = inputAndExpectedOutput.getRight();

        // Determines the path where to write the file generated in this test
        String inputPath = inputURL.getPath();
        String outputPath = OUTPUT_DIR + inputPath.substring(inputPath.lastIndexOf(INPUT_DIR) + INPUT_DIR.length());
        outputPath = outputPath.substring(0, outputPath.length() - EYWA_EXTENSION.length()) + IFC_EXTENSION;

        // Deserializes the eywa file in an EywaRoot instance, with all its
        // content.
        EywaRoot eywaRoot = objectMapper.readValue(inputURL, EywaRoot.class);

        // Performs the conversion and writes the result to file
        reader.convert(eywaRoot);
        IfcProject result = (IfcProject) reader.getResult();
        EywaToIfcConverter.writeToFile(result, outputPath);

        // Verifies that the converted file is what we expect it to be
        String expectedOutputContent = getDataSection(expectedOutputURL.getPath());
        String outputContent = getDataSection(outputPath);
        Assert.assertEquals(expectedOutputContent, outputContent);
    }
}
