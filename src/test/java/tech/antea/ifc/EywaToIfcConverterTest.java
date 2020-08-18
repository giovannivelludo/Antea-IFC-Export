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

import buildingsmart.ifc.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.EywaRoot;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class EywaToIfcConverterTest {
    private final ScanResult r =
            new ClassGraph().enableStaticFinalFieldConstantInitializerValues()
                    .enableAllInfo().whitelistPackages(LoadEywaTest.PACKAGE)
                    .scan();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private static String getDataSection(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath),
                                                StandardCharsets.US_ASCII);
        StringBuilder dataSection = new StringBuilder();
        for (int i = 6; i < lines.size() - 1; i++) {
            // DATA section starts at line 6 (if the first one is line 0) and
            // ends at the penultimate line
            dataSection.append(lines.get(i)).append("\n");
        }
        return dataSection.toString();
    }

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
        String filePath = "./ifc-out/shell.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcRelDecomposes projectDecomposer =
                result.getIsDecomposedBy().iterator().next();
        IfcSite site =
                (IfcSite) projectDecomposer.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure geometriesContainer =
                site.getContainsElements().iterator().next();

        IfcProduct shell =
                geometriesContainer.getRelatedElements().iterator().next();

        String expectedDataSection =
                "DATA;\n" + "#1=IFCPERSON($,$,'',$,$,$,$,$);\n" +
                        "#2=IFCACTORROLE(.CONSULTANT.,$,$);\n" +
                        "#3=IFCORGANIZATION($,'Antea',$,(#2),$);\n" +
                        "#4=IFCPERSONANDORGANIZATION(#1,#3,$);\n" +
                        "#5=IFCAPPLICATION(#3,'0.0.1-SNAPSHOT','Antea IFC " +
                        "Export'," + "'com.anteash:ifc');\n" +
                        "#6=IFCOWNERHISTORY(#4,#5,$,.ADDED.," +
                        modifiedDate.serialize() + ",#4,#5," +
                        creationDate.serialize() + ");\n" +
                        "#7=IFCCARTESIANPOINT((0.0,0.0,0.0));\n" +
                        "#8=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#9=IFCDIRECTION((1.0,0.0,0.0));\n" +
                        "#10=IFCAXIS2PLACEMENT3D(#7,#8,#9);\n" +
                        "#11=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan'," +
                        "'Model',3,1" + ".0E-8,#10,$);\n" +
                        "#12=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#13=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#14=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#15=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#16=IFCUNITASSIGNMENT((#12,#13,#14,#15));\n" +
                        "#17=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," +
                        "'05-ML-120-0-013-1/2\"-02A-V',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + site.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCCARTESIANPOINT((150841.07,127501.77,1118.74))" +
                        ";\n" + "#20=IFCAXIS2PLACEMENT3D(#19,#8,#9);\n" +
                        "#21=IFCLOCALPLACEMENT($,#20);\n" +
                        "#22=IFCCARTESIANPOINT((8.28,114.71));\n" +
                        "#23=IFCDIRECTION((1.0,0.0));\n" +
                        "#24=IFCAXIS2PLACEMENT2D(#22,#23);\n" +
                        "#25=IFCTRAPEZIUMPROFILEDEF(.AREA.,$,#24,4.78,4.78," +
                        "229.42,0" + ".0);\n" +
                        "#26=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#7,#26,#9);\n" +
                        "#28=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#29=IFCAXIS1PLACEMENT(#7,#28);\n" +
                        "#30=IFCREVOLVEDAREASOLID(#25,#27,#29,6" +
                        ".283185307179586);\n" +
                        "#31=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#30));\n" +
                        "#32=IFCPRODUCTDEFINITIONSHAPE($,$,(#31));\n" +
                        "#33=IFCPROXY(" + shell.getGlobalId().serialize() +
                        ",#6,'Shell'," + "'{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBTUBI\",\\X\\0A  \"ADESCR\" : " +
                        "\"TB5293154\",\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 21.34,\\X\\0A  \"ASCHEDULE\" : " +
                        "\"160\"," +
                        "\\X\\0A  \"AMAT\" : \"CARBON STEEL\",\\X\\0A  " +
                        "\"ITIPO_STD\" " +
                        ": \"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\"," +
                        "\\X\\0A  " +
                        "\"AGRUPPO_SCH\" : \"A02\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A" +
                        "  \"NLUNGHEZZA\" : 229.420999999988\\X\\0A}',$,#21," +
                        "#32," + ".PRODUCT.,$);\n" +
                        "#34=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        geometriesContainer.getGlobalId().serialize() +
                        ",#6,'Site to geometries " + "link',$," +
                        "(#33),#18);\n" + "#35=IFCRELAGGREGATES(" +
                        projectDecomposer.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }
}
