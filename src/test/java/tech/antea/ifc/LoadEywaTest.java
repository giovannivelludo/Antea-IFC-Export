package tech.antea.ifc;

import buildingsmart.ifc.IfcProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.EywaRoot;
import it.imc.persistence.po.eytukan.Primitive;
import lombok.RequiredArgsConstructor;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;

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
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScanResult r =
            new ClassGraph().enableStaticFinalFieldConstantInitializerValues()
                    .enableAllInfo().whitelistPackages(PACKAGE).scan();
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

    @Test
    public void testFile() throws IOException {
        // Deserializes the eywa file in an EywRoot instance, with all its
        // content.
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        Primitive object = eywaRoot.getObject();
        MatcherAssert.assertThat(object, is(notNullValue()));
    }

    @Test
    public void test_051200ALR1A() throws IOException {
        URL file =
                r.getResourcesWithLeafName("051200ALR1A.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/app/051200ALR1A.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

    }
}
