/*
 * This file is part of Antea IFC Export.
 *
 * Author: Giovanni Velludo
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Giovanni Velludo
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.EywaRoot;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

public class EywaToIfcConverterTest {
    private final ScanResult r =
            new ClassGraph().enableStaticFinalFieldConstantInitializerValues()
                    .enableAllInfo().whitelistPackages(LoadEywaTest.PACKAGE)
                    .scan();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tests the conversion of an EywaRoot containing only one Shell.
     */
    @Test
    public void convert_Shell() throws IOException {
        URL file = r.getResourcesWithLeafName("shell.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        EywaToIfcConverter.writeToFile(result, "./ifc-out/shell.ifc");
    }
}
