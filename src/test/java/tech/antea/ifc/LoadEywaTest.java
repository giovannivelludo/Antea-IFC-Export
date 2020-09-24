package tech.antea.ifc;

import buildingsmart.ifc.IfcProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.EywaRoot;
import lombok.RequiredArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.net.URL;

/**
 * This test verifies the conversion of Eywa Models. It is a {@code
 * Parametrized} test: the method annotated {@code @Parameters} creates an
 * {@code Iterable} of {@code URL}s. This test gets instantiated once for every
 * URL using the constructor {@code LoadEywaTest(Url url)} that is provided by
 * the {@code @RequiredArgsConstructor} annotation. Then, the {@code @Test}
 * methods are executed.
 */
@RequiredArgsConstructor
@RunWith(value = Parameterized.class)
public class LoadEywaTest {
    public static final String PACKAGE = "tech.antea";
    private static final char S = java.io.File.separatorChar;
    private static final String EYWA_DIR =
            S + "tech" + S + "antea" + S + "models" + S + "cluster" + S;
    private static final String IFC_DIR = "." + S + "ifc-out" + S;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScanResult r =
            new ClassGraph().enableStaticFinalFieldConstantInitializerValues()
                    .enableAllInfo().whitelistPackages(PACKAGE).scan();
    private static final EywaReader reader =
            new EywaReader(new EywaToIfcConverter());

    private final URL file;

    /**
     * This method finds all the "*.eywa" resources inside the {@code
     * tech.antea} package. It uses the ClassGraph library to do the reflection
     * and the classpath scanning.
     */
    @Parameters(name = "{0}")
    public static Iterable<URL> data() {
        return r.getResourcesWithExtension("eywa").getURLs();
    }

    /**
     * Converts each Eywa file from the resources directory to an IFC file.
     */
    @Test
    public void testFile() throws IOException {
        // Deserializes the eywa file in an EywaRoot instance, with all its
        // content.
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        String inputFilePath = file.getPath();
        String outputFilePath = IFC_DIR + inputFilePath.substring(
                inputFilePath.lastIndexOf(EYWA_DIR) + EYWA_DIR.length());
        // changing the extension from .eywa to .ifc
        outputFilePath =
                outputFilePath.substring(0, outputFilePath.length() - 4) +
                        "ifc";

        reader.convert(eywaRoot);
        IfcProject result = (IfcProject) reader.getResult();
        EywaToIfcConverter.writeToFile(result, outputFilePath);
    }
}
