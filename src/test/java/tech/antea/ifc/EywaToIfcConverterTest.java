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
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
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
                        "#33=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
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
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#33),#18);\n" + "#35=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Blind.
     */
    @Test
    public void convert_Blind() throws IOException {
        URL file = r.getResourcesWithLeafName("blind.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/blind.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCCARTESIANPOINT((150897.92,115859.01,1300.89))" +
                        ";\n" + "#20=IFCDIRECTION((-1.0,0.0,0.0));\n" +
                        "#21=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#22=IFCAXIS2PLACEMENT3D(#19,#20,#21);\n" +
                        "#23=IFCLOCALPLACEMENT($,#22);\n" +
                        "#24=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#25=IFCDIRECTION((1.0,0.0));\n" +
                        "#26=IFCAXIS2PLACEMENT2D(#24,#25);\n" +
                        "#27=IFCCIRCLEPROFILEDEF(.AREA.,$,#26,320.68);\n" +
                        "#28=IFCEXTRUDEDAREASOLID(#27,#10,#8,85.85);\n" +
                        "#29=IFCCIRCLEPROFILEDEF(.AREA.,$,#26,177.8);\n" +
                        "#30=IFCCARTESIANPOINT((0.0,0.0,85.85));\n" +
                        "#31=IFCAXIS2PLACEMENT3D(#30,#8,#9);\n" +
                        "#32=IFCEXTRUDEDAREASOLID(#29,#31,#8,17" +
                        ".169999999999998);\n" +
                        "#33=IFCBOOLEANRESULT(.UNION.,#28,#32);\n" +
                        "#34=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33));" +
                        "\n" + "#35=IFCPRODUCTDEFINITIONSHAPE($,$,(#34));\n" +
                        "#36=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Blind'," + "'{\\X\\0A  " +
                        "\"CATEGORY\" : \"APPFLANGE\",\\X\\0A  \"ADESCR\" : " +
                        "\"Cieca\",\\X\\0A  \"NDIAMETRO\" : 355.6,\\X\\0A  " +
                        "\"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : " +
                        "\"\",\\X\\0A  \"NDIAM_CORONA\" : 641.35,\\X\\0A  " +
                        "\"NSPESS\" " +
                        ": 85.85,\\X\\0A  \"ITIPO_FLANGIA\" : " +
                        "\"100-CIECA\"\\X\\0A}'," +
                        "$,#23,#35,.PRODUCT.,$);\n" +
                        "#37=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#36),#18);\n" + "#38=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Curve.
     */
    @Test
    public void convert_Curve() throws IOException {
        URL file = r.getResourcesWithLeafName("curve.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/curve.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,38.1));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,16.7,4" +
                        ".55);\n" + "#24=IFCAXIS1PLACEMENT(#7,#9);\n" +
                        "#25=IFCREVOLVEDAREASOLID(#23,#10,#24,0.79);\n" +
                        "#26=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#25));\n" +
                        "#27=IFCPRODUCTDEFINITIONSHAPE($,$,(#26));\n" +
                        "#28=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Curve'," + "'{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBCURVE\",\\X\\0A  \"ADESCR\" : " +
                        "\"New\"," +
                        "\\X\\0A  \"NANGOLO\" : 45.0000010633437,\\X\\0A  " +
                        "\"NDIAMETRO\" : 33.4,\\X\\0A  \"ASCHEDULE\" : " +
                        "\"80\",\\X\\0A" +
                        "  \"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : " +
                        "\"\",\\X\\0A  \"AGRUPPO_SCH\" : \"A08\",\\X\\0A  " +
                        "\"NRAGGIO\"" +
                        " : 38.1000000000058,\\X\\0A  \"NSPESS\" : 4.55," +
                        "\\X\\0A  " +
                        "\"NLUNGHEZZA\" : 29.9236707325395,\\X\\0A  " +
                        "\"ITIPO_CURVA\" :" +
                        " \"2-CURVA_LST\"\\X\\0A}',$,#19,#27,.PRODUCT.,$);\n" +
                        "#29=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#28),#18);\n" + "#30=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Endplate.
     */
    @Test
    public void convert_Endplate() throws IOException {
        URL file = r.getResourcesWithLeafName("endplate.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/endplate.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

        String expectedDataSection =
                "DATA;\n" + "#1=IFCPERSON($,$,'',$,$,$,$,$);\n" +
                        "#2=IFCACTORROLE(.CONSULTANT.,$,$);\n" +
                        "#3=IFCORGANIZATION($,'Antea',$,(#2),$);\n" +
                        "#4=IFCPERSONANDORGANIZATION(#1,#3,$);\n" +
                        "#5=IFCAPPLICATION(#3,'0.0.1-SNAPSHOT','Antea IFC " +
                        "Export','com.anteash:ifc');\n" +
                        "#6=IFCOWNERHISTORY(#4,#5,$,.ADDED.," +
                        modifiedDate.serialize() + ",#4,#5," +
                        creationDate.serialize() + ");\n" +
                        "#7=IFCCARTESIANPOINT((0.0,0.0,0.0));\n" +
                        "#8=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#9=IFCDIRECTION((1.0,0.0,0.0));\n" +
                        "#10=IFCAXIS2PLACEMENT3D(#7,#8,#9);\n" +
                        "#11=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan'," +
                        "'Model',3,1.0E-8,#10,$);\n" +
                        "#12=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#13=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#14=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#15=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#16=IFCUNITASSIGNMENT((#12,#13,#14,#15));\n" +
                        "#17=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#19=IFCCARTESIANPOINT((0.0,0.0,560.0));\n" +
                        "#20=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#21=IFCAXIS2PLACEMENT3D(#19,#20,#9);\n" +
                        "#22=IFCLOCALPLACEMENT($,#21);\n" +
                        "#23=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#24=IFCDIRECTION((1.0,0.0));\n" +
                        "#25=IFCAXIS2PLACEMENT2D(#23,#24);\n" +
                        "#26=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#25,1010.0,10" +
                        ".0);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#10,#8,50.0);\n" +
                        "#28=IFCELLIPSEPROFILEDEF(.AREA.,$,#25,1010.0,510.0);" +
                        "\n" + "#29=IFCCARTESIANPOINT((0.0,0.0,50.0));\n" +
                        "#30=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#31=IFCAXIS2PLACEMENT3D(#29,#30,#9);\n" +
                        "#32=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#33=IFCAXIS1PLACEMENT(#7,#32);\n" +
                        "#34=IFCREVOLVEDAREASOLID(#28,#31,#33,3" +
                        ".141592653589793);\n" +
                        "#35=IFCELLIPSEPROFILEDEF(.AREA.,$,#25,1000.0,500.0);" +
                        "\n" + "#36=IFCREVOLVEDAREASOLID(#35,#31,#33,3" +
                        ".141592653589793);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#34,#36);\n" +
                        "#38=IFCAXIS2PLACEMENT3D(#29,#8,#9);\n" +
                        "#39=IFCPLANE(#38);\n" +
                        "#40=IFCHALFSPACESOLID(#39,.T.);\n" +
                        "#41=IFCBOOLEANCLIPPINGRESULT(.DIFFERENCE.,#37,#40);" +
                        "\n" + "#42=IFCBOOLEANRESULT(.UNION.,#27,#41);\n" +
                        "#43=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#42));" +
                        "\n" + "#44=IFCPRODUCTDEFINITIONSHAPE($,$,(#43));\n" +
                        "#45=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Endplate'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"APPFONDI\",\\X\\0A  " +
                        "\"ADESCR\" : \"Fondo bombato\",\\X\\0A  " +
                        "\"NDIAMETRO\" : 2020,\\X\\0A  \"ITIPO_FONDO\" : " +
                        "\"2-TF_BOMBATO\",\\X\\0A  \"NCOLLETTO\" : 50,\\X\\0A" +
                        "  \"NBOMBATURA\" : 510,\\X\\0A  \"NSPESS\" : 10," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 560\\X\\0A}',$,#22,#44," +
                        ".PRODUCT.,$);\n" +
                        "#46=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#45),#18);\n" + "#47=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Nozzle with no
     * trunk.
     */
    @Test
    public void convert_Nozzle_noTrunk() throws IOException {
        URL file = r.getResourcesWithLeafName("nozzle-no-trunk.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/nozzle-no-trunk.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$,.COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,10.67,1" +
                        ".0);\n" + "#24=IFCCARTESIANPOINT((0.0,0.0,10.0));\n" +
                        "#25=IFCAXIS2PLACEMENT3D(#24,#8,#9);\n" +
                        "#26=IFCEXTRUDEDAREASOLID(#23,#25,#8,1.0);\n" +
                        "#27=IFCCARTESIANPOINT((0.0,0.0,11.0));\n" +
                        "#28=IFCAXIS2PLACEMENT3D(#27,#8,#9);\n" +
                        "#29=IFCEXTRUDEDAREASOLID(#23,#28,#8,0.1);\n" +
                        "#30=IFCBOOLEANRESULT(.UNION.,#26,#29);\n" +
                        "#31=IFCCARTESIANPOINT((10.17,5.0));\n" +
                        "#32=IFCAXIS2PLACEMENT2D(#31,#21);\n" +
                        "#33=IFCTRAPEZIUMPROFILEDEF(.AREA.,$,#32,1.0,1.0,10" +
                        ".0,0.0);\n" + "#34=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#35=IFCAXIS2PLACEMENT3D(#7,#34,#9);\n" +
                        "#36=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#37=IFCAXIS1PLACEMENT(#7,#36);\n" +
                        "#38=IFCREVOLVEDAREASOLID(#33,#35,#37,6" +
                        ".283185307179586);\n" +
                        "#39=IFCBOOLEANRESULT(.UNION.,#30,#38);\n" +
                        "#40=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#39));" +
                        "\n" + "#41=IFCPRODUCTDEFINITIONSHAPE($,$,(#40));\n" +
                        "#42=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle','{\\X\\0A " +
                        " \"CATEGORY\" : \"APPFLANGE\",\\X\\0A  \"ADESCR\" : " +
                        "\"Flangia\",\\X\\0A  \"NDIAMETRO\" : 21.34,\\X\\0A  " +
                        "\"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : " +
                        "\"\",\\X\\0A  \"NDIAM_CORONA\" : 21.34,\\X\\0A  " +
                        "\"NCODOLO\" " +
                        ": 10,\\X\\0A  \"NSPESS\" : 1,\\X\\0A  \"NLUNGHEZZA\"" +
                        " : 11," +
                        "\\X\\0A  \"ITIPO_FLANGIA\" : \"0-B165_SW\"\\X\\0A}'," +
                        "$,#19," + "#41,.PRODUCT.,$);\n" +
                        "#43=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#42),#18);\n" +
                        "#44=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Nozzle with no
     * tang.
     */
    @Test
    public void convert_Nozzle_noTang() throws IOException {
        URL file = r.getResourcesWithLeafName("nozzle-no-tang.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/nozzle-no-tang.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$,.COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,190.5,114" +
                        ".3);\n" +
                        "#24=IFCCARTESIANPOINT((0.0,0.0,187.62));\n" +
                        "#25=IFCAXIS2PLACEMENT3D(#24,#8,#9);\n" +
                        "#26=IFCEXTRUDEDAREASOLID(#23,#25,#8,55.56);\n" +
                        "#27=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,141.82,65" +
                        ".61999999999999);\n" +
                        "#28=IFCCARTESIANPOINT((0.0,0.0,243.18));\n" +
                        "#29=IFCAXIS2PLACEMENT3D(#28,#8,#9);\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#27,#29,#8,5.556);\n" +
                        "#31=IFCBOOLEANRESULT(.UNION.,#26,#30);\n" +
                        "#32=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,117.48,41" +
                        ".28);\n" +
                        "#33=IFCEXTRUDEDAREASOLID(#32,#10,#8,187.62);\n" +
                        "#34=IFCBOOLEANRESULT(.UNION.,#31,#33);\n" +
                        "#35=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#34));" +
                        "\n" + "#36=IFCPRODUCTDEFINITIONSHAPE($,$,(#35));\n" +
                        "#37=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle','{\\X\\0A " +
                        " \"CATEGORY\" : \"BOCCHELLI\",\\X\\0A  \"ADESCR\" : " +
                        "\"N1\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 1,\\X\\0A  \"NDIAMETRO\" :" +
                        " 234.95," + "\\X\\0A  \"ACLASS\" : \"900\",\\X\\0A  " +
                        "\"NTRONCHETTO\" : 187" +
                        ".62,\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"NDIAM_CORONA\" :" +
                        " 381," +
                        "\\X\\0A  \"NSPESS\" : 41.275,\\X\\0A  \"NLUNGHEZZA\"" +
                        " : 243" +
                        ".183,\\X\\0A  \"ITIPO_FLANGIA\" : 3\\X\\0A}',$,#19," +
                        "#36," + ".PRODUCT.,$);\n" +
                        "#38=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#37),#18);\n" +
                        "#39=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Nozzle.
     */
    @Test
    public void convert_Nozzle() throws IOException {
        URL file = r.getResourcesWithLeafName("nozzle.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/nozzle.ifc";
        EywaToIfcConverter.writeToFile(result, filePath);

        IfcTimeStamp modifiedDate =
                result.getOwnerHistory().getLastModifiedDate();
        IfcTimeStamp creationDate = result.getOwnerHistory().getCreationDate();
        IfcRelDecomposes ifcRelAggregates =
                result.getIsDecomposedBy().iterator().next();
        IfcSite ifcSite =
                (IfcSite) ifcRelAggregates.getRelatedObjects().iterator()
                        .next();
        IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure =
                ifcSite.getContainsElements().iterator().next();
        IfcProxy ifcProxy = (IfcProxy) ifcRelContainedInSpatialStructure
                .getRelatedElements().iterator().next();

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
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#11),#16);\n" +
                        "#18=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$,.COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,190.5,114" +
                        ".3);\n" +
                        "#24=IFCCARTESIANPOINT((0.0,0.0,267.62));\n" +
                        "#25=IFCAXIS2PLACEMENT3D(#24,#8,#9);\n" +
                        "#26=IFCEXTRUDEDAREASOLID(#23,#25,#8,55.56);\n" +
                        "#27=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,141.82,65" +
                        ".61999999999999);\n" +
                        "#28=IFCCARTESIANPOINT((0.0,0.0,323.18));\n" +
                        "#29=IFCAXIS2PLACEMENT3D(#28,#8,#9);\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#27,#29,#8,5.556);\n" +
                        "#31=IFCBOOLEANRESULT(.UNION.,#26,#30);\n" +
                        "#32=IFCCARTESIANPOINT((96.84,40.0));\n" +
                        "#33=IFCAXIS2PLACEMENT2D(#32,#21);\n" +
                        "#34=IFCTRAPEZIUMPROFILEDEF(.AREA.,$,#33,41.28,65" +
                        ".61999999999999,80.0,0.0);\n" +
                        "#35=IFCCARTESIANPOINT((0.0,0.0,187.62));\n" +
                        "#36=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#37=IFCAXIS2PLACEMENT3D(#35,#36,#9);\n" +
                        "#38=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#39=IFCAXIS1PLACEMENT(#7,#38);\n" +
                        "#40=IFCREVOLVEDAREASOLID(#34,#37,#39,6" +
                        ".283185307179586);\n" +
                        "#41=IFCBOOLEANRESULT(.UNION.,#31,#40);\n" +
                        "#42=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,117.48,41" +
                        ".28);\n" +
                        "#43=IFCEXTRUDEDAREASOLID(#42,#10,#8,187.62);\n" +
                        "#44=IFCBOOLEANRESULT(.UNION.,#41,#43);\n" +
                        "#45=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#44));" +
                        "\n" + "#46=IFCPRODUCTDEFINITIONSHAPE($,$,(#45));\n" +
                        "#47=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle','{\\X\\0A " +
                        " \"CATEGORY\" : \"BOCCHELLI\",\\X\\0A  \"ADESCR\" : " +
                        "\"N1\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 1,\\X\\0A  \"NDIAMETRO\" :" +
                        " 234.95," + "\\X\\0A  \"ACLASS\" : \"900\",\\X\\0A  " +
                        "\"NTRONCHETTO\" : 187" +
                        ".62,\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"NDIAM_CORONA\" :" +
                        " 381," +
                        "\\X\\0A  \"NSPESS\" : 41.275,\\X\\0A  \"NLUNGHEZZA\"" +
                        " : 243" +
                        ".183,\\X\\0A  \"ITIPO_FLANGIA\" : 3\\X\\0A}',$,#19," +
                        "#46," + ".PRODUCT.,$);\n" +
                        "#48=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#47),#18);\n" +
                        "#49=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }
}
