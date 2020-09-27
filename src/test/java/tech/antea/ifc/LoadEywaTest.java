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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>This test verifies the conversion of Eywa Models. It is a {@code
 * Parametrized} test: the method annotated {@code @Parameters} creates an
 * {@code Iterable} of {@code Pair}s of {@code URL}s. This test gets
 * instantiated once for every pair of URLs using the constructor {@link
 * LoadEywaTest#LoadEywaTest(Pair)} that is provided by the {@code
 *
 * @RequiredArgsConstructor} annotation. Then, the {@code @Test} methods are
 * executed.</p>
 * <p>In order for tests to work, for each .eywa file in the test
 * resources there must be a corresponding expected .ifc file in the test
 * resources.</p>
 */
@RequiredArgsConstructor
@RunWith(value = Parameterized.class)
public class LoadEywaTest {
    public static final String PACKAGE = "tech.antea";
    private static final String EYWA_EXTENSION = "eywa";
    private static final String IFC_EXTENSION = "ifc";
    private static final char S = java.io.File.separatorChar;
    private static final String INPUT_DIR =
            S + "tech" + S + "antea" + S + "models" + S + "cluster" + S;
    private static final String OUTPUT_DIR = "." + S + "ifc-out" + S;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final EywaReader reader =
            new EywaReader(new EywaToIfcConverter());

    private final Pair<URL, URL> inputAndExpectedOutput;

    /**
     * This method finds all the "*.eywa" and "*.ifc" resources inside the
     * {@code tech.antea} package. It uses the ClassGraph library to do the
     * reflection and the classpath scanning. Assuming that each eywa file has a
     * corresponding expected ifc file, the first will be the left element of
     * the pair and the second the right element of the pair.
     */
    @Parameters(name = "{0}")
    public static Iterable<Pair<URL, URL>> data() {
        ScanResult r = new ClassGraph()
                .enableStaticFinalFieldConstantInitializerValues()
                .enableAllInfo().whitelistPackages(PACKAGE).scan();
        List<URL> eywaUrls =
                r.getResourcesWithExtension(EYWA_EXTENSION).getURLs();
        List<URL> expectedIfcUrls =
                r.getResourcesWithExtension(IFC_EXTENSION).getURLs();
        r.close();
        eywaUrls.sort(Comparator.comparing(URL::getFile));
        expectedIfcUrls.sort(Comparator.comparing(URL::getFile));
        return IntStream.range(0, eywaUrls.size()).mapToObj(i -> new Pair<>(
                eywaUrls.get(i),
                expectedIfcUrls.get(i))).collect(Collectors.toList());
    }

    /**
     * @param filePath The path to the IFC file of which to retrieve the DATA
     *                 section.
     * @return The DATA section of the IFC file.
     *
     * @throws IOException       If an I/O error occurs reading from the file or
     *                           a malformed or unmappable byte sequence is
     *                           read.
     * @throws SecurityException In the case of the default provider, and a
     *                           security manager is installed, the {@link
     *                           SecurityManager#checkRead(String) checkRead}
     *                           method is invoked to check read access to the
     *                           file.
     */
    private static String getDataSection(@NonNull String filePath)
            throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath),
                                                StandardCharsets.US_ASCII);
        removeNonDeterministicOutput(lines);
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
     *
     * @param strings The input List of Strings.
     */
    private static void removeNonDeterministicOutput(@NonNull List<String> strings) {
        strings.replaceAll(s -> {
            if (s.matches("^#[0-9]+=IFCOWNERHISTORY\\(.*")) {
                return s.replaceAll("[0-9]{10,}", "");
            } else if (s.matches("^#[0-9]+=IFCPROJECT\\(.*") ||
                    s.matches("^#[0-9]+=IFCSITE\\(.*") ||
                    s.matches("^#[0-9]+=IFCRELAGGREGATES\\(.*") ||
                    s.matches("^#[0-9]+=IFCPROXY\\(.*") || s.matches(
                    "^#[0-9]+=IFCRELCONTAINEDINSPATIALSTRUCTURE\\(" + ".*")) {
                return s.replaceFirst(Matcher.quoteReplacement(
                        "'[0-9A-Za-z_$]+'"), "''");
            }
            return s;
        });
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
        String outputPath = OUTPUT_DIR + inputPath.substring(
                inputPath.lastIndexOf(INPUT_DIR) + INPUT_DIR.length());
        outputPath = outputPath
                .substring(0, outputPath.length() - EYWA_EXTENSION.length()) +
                IFC_EXTENSION;

        // Deserializes the eywa file in an EywaRoot instance, with all its
        // content.
        EywaRoot eywaRoot = objectMapper.readValue(inputURL, EywaRoot.class);

        // Performs the conversion and writes the result to file
        reader.convert(eywaRoot);
        IfcProject result = (IfcProject) reader.getResult();
        EywaToIfcConverter.writeToFile(result, outputPath);

        // Verifies that the converted file is what we expect it to be
        String expectedOutputContent =
                getDataSection(expectedOutputURL.getPath());
        String outputContent = getDataSection(outputPath);
        Assert.assertEquals(expectedOutputContent, outputContent);
    }
}
