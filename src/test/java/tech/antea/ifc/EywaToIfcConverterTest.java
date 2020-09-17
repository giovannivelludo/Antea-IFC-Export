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
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((5.89,0.0));\n" +
                        "#21=IFCCARTESIANPOINT((10.67,0.0));\n" +
                        "#22=IFCCARTESIANPOINT((10.67,229.42));\n" +
                        "#23=IFCCARTESIANPOINT((5.89,229.42));\n" +
                        "#24=IFCPOLYLINE((#20,#21,#22,#23));\n" +
                        "#25=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#24);\n" +
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
                        "  \"NLUNGHEZZA\" : 229.420999999988\\X\\0A}',$,#19," +
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
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEPROFILEDEF(.AREA.,$,#22,320.68);\n" +
                        "#24=IFCEXTRUDEDAREASOLID(#23,#10,#8,85.85);\n" +
                        "#25=IFCCIRCLEPROFILEDEF(.AREA.,$,#22,177.8);\n" +
                        "#26=IFCCARTESIANPOINT((0.0,0.0,85.85));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#26,#8,#9);\n" +
                        "#28=IFCEXTRUDEDAREASOLID(#25,#27,#8,8" +
                        ".584999999999999);\n" +
                        "#29=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#24,#28)" + ");\n" +
                        "#30=IFCPRODUCTDEFINITIONSHAPE($,$,(#29));\n" +
                        "#31=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Blind'," + "'{\\X\\0A  " +
                        "\"CATEGORY\" : \"APPFLANGE\",\\X\\0A  \"ADESCR\" : " +
                        "\"Cieca\",\\X\\0A  \"NDIAMETRO\" : 355.6,\\X\\0A  " +
                        "\"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : " +
                        "\"\",\\X\\0A  \"NDIAM_CORONA\" : 641.35,\\X\\0A  " +
                        "\"NSPESS\" " +
                        ": 85.85,\\X\\0A  \"ITIPO_FLANGIA\" : " +
                        "\"100-CIECA\"\\X\\0A}'," +
                        "$,#19,#30,.PRODUCT.,$);\n" +
                        "#32=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#31),#18);\n" + "#33=IFCRELAGGREGATES(" +
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
                        creationDate.serialize() + ",#4,#5," +
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
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,10.67,1" +
                        ".0);\n" + "#24=IFCCARTESIANPOINT((0.0,0.0,10.0));\n" +
                        "#25=IFCAXIS2PLACEMENT3D(#24,#8,#9);\n" +
                        "#26=IFCEXTRUDEDAREASOLID(#23,#25,#8,1.0);\n" +
                        "#27=IFCCARTESIANPOINT((9.67,0.0));\n" +
                        "#28=IFCCARTESIANPOINT((10.67,0.0));\n" +
                        "#29=IFCCARTESIANPOINT((10.67,10.0));\n" +
                        "#30=IFCCARTESIANPOINT((9.67,10.0));\n" +
                        "#31=IFCPOLYLINE((#27,#28,#29,#30));\n" +
                        "#32=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#31);\n" +
                        "#33=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#34=IFCAXIS2PLACEMENT3D(#7,#33,#9);\n" +
                        "#35=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#36=IFCAXIS1PLACEMENT(#7,#35);\n" +
                        "#37=IFCREVOLVEDAREASOLID(#32,#34,#36,6" +
                        ".283185307179586);\n" +
                        "#38=IFCCARTESIANPOINT((0.0,0.0,11.0));\n" +
                        "#39=IFCAXIS2PLACEMENT3D(#38,#8,#9);\n" +
                        "#40=IFCEXTRUDEDAREASOLID(#23,#39,#8,0.1);\n" +
                        "#41=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#26,#37," + "#40));\n" +
                        "#42=IFCPRODUCTDEFINITIONSHAPE($,$,(#41));\n" +
                        "#43=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle'," + "'{\\X\\0A " +
                        " \"CATEGORY\" : \"APPFLANGE\",\\X\\0A  \"ADESCR\" : " +
                        "\"Flangia\",\\X\\0A  \"NDIAMETRO\" : 21.34,\\X\\0A  " +
                        "\"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : " +
                        "\"\",\\X\\0A  \"NDIAM_CORONA\" : 21.34,\\X\\0A  " +
                        "\"NCODOLO\" " +
                        ": 10,\\X\\0A  \"NSPESS\" : 1,\\X\\0A  \"NLUNGHEZZA\"" +
                        " : 11," +
                        "\\X\\0A  \"ITIPO_FLANGIA\" : \"0-B165_SW\"\\X\\0A}'," +
                        "$,#19," + "#42,.PRODUCT.,$);\n" +
                        "#44=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#43),#18);\n" + "#45=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
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
                        ",#6,$,$,$,$,$,$," + ".COMPLEX" + ".,$,$,$,$,$);\n" +
                        "#19=IFCLOCALPLACEMENT($,#10);\n" +
                        "#20=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#21=IFCDIRECTION((1.0,0.0));\n" +
                        "#22=IFCAXIS2PLACEMENT2D(#20,#21);\n" +
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,117.48,41" +
                        ".28);\n" +
                        "#24=IFCEXTRUDEDAREASOLID(#23,#10,#8,187.62);\n" +
                        "#25=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,190.5,114" +
                        ".3);\n" +
                        "#26=IFCCARTESIANPOINT((0.0,0.0,187.62));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#26,#8,#9);\n" +
                        "#28=IFCEXTRUDEDAREASOLID(#25,#27,#8,55.56);\n" +
                        "#29=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,141.82,65" +
                        ".61999999999999);\n" +
                        "#30=IFCCARTESIANPOINT((0.0,0.0,243.18));\n" +
                        "#31=IFCAXIS2PLACEMENT3D(#30,#8,#9);\n" +
                        "#32=IFCEXTRUDEDAREASOLID(#29,#31,#8,5.556);\n" +
                        "#33=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#24,#28," + "#32));\n" +
                        "#34=IFCPRODUCTDEFINITIONSHAPE($,$,(#33));\n" +
                        "#35=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle'," + "'{\\X\\0A " +
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
                        "#34," + ".PRODUCT.,$);\n" +
                        "#36=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$," + "(#35),#18);\n" + "#37=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to" + " site link',$,#17,(#18));\n" +
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
                        "#23=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,117.48,41" +
                        ".28);\n" +
                        "#24=IFCEXTRUDEDAREASOLID(#23,#10,#8,187.62);\n" +
                        "#25=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,141.82,65" +
                        ".61999999999999);\n" +
                        "#26=IFCCARTESIANPOINT((0.0,0.0,323.18));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#26,#8,#9);\n" +
                        "#28=IFCEXTRUDEDAREASOLID(#25,#27,#8,5.556);\n" +
                        "#29=IFCCARTESIANPOINT((76.2,0.0));\n" +
                        "#30=IFCCARTESIANPOINT((117.48,0.0));\n" +
                        "#31=IFCCARTESIANPOINT((141.82,80.0));\n" +
                        "#32=IFCCARTESIANPOINT((76.2,80.0));\n" +
                        "#33=IFCPOLYLINE((#29,#30,#31,#32));\n" +
                        "#34=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#33);\n" +
                        "#35=IFCCARTESIANPOINT((0.0,0.0,187.62));\n" +
                        "#36=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#37=IFCAXIS2PLACEMENT3D(#35,#36,#9);\n" +
                        "#38=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#39=IFCAXIS1PLACEMENT(#7,#38);\n" +
                        "#40=IFCREVOLVEDAREASOLID(#34,#37,#39,6" +
                        ".283185307179586);\n" +
                        "#41=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#22,190.5,114" +
                        ".3);\n" +
                        "#42=IFCCARTESIANPOINT((0.0,0.0,267.62));\n" +
                        "#43=IFCAXIS2PLACEMENT3D(#42,#8,#9);\n" +
                        "#44=IFCEXTRUDEDAREASOLID(#41,#43,#8,55.56);\n" +
                        "#45=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#24,#28,#40,#44));\n" +
                        "#46=IFCPRODUCTDEFINITIONSHAPE($,$,(#45));\n" +
                        "#47=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Nozzle'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"BOCCHELLI\",\\X\\0A  " +
                        "\"ADESCR\" : \"N1\",\\X\\0A  \"NCOR_ALLOW\" : 1," +
                        "\\X\\0A  \"NDIAMETRO\" : 234.95,\\X\\0A  \"ACLASS\" " +
                        ": \"900\",\\X\\0A  \"NTRONCHETTO\" : 187.62,\\X\\0A " +
                        " \"ITIPO_STD\" : \"0-ANSI\",\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"NDIAM_CORONA\" :" +
                        " 381,\\X\\0A  \"NSPESS\" : 41.275,\\X\\0A  " +
                        "\"NLUNGHEZZA\" : 243.183,\\X\\0A  \"ITIPO_FLANGIA\" " +
                        ": 3\\X\\0A}',$,#19,#46,.PRODUCT.,$);\n" +
                        "#48=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#47),#18);\n" + "#49=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Valve.
     */
    @Test
    public void convert_Valve() throws IOException {
        URL file = r.getResourcesWithLeafName("valve.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/valve.ifc";
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
                        "#21=IFCCARTESIANPOINT((100.0,0.0));\n" +
                        "#22=IFCCARTESIANPOINT((100.0,30.0));\n" +
                        "#23=IFCCARTESIANPOINT((0.0,30.0));\n" +
                        "#24=IFCPOLYLINE((#20,#21,#22,#23));\n" +
                        "#25=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#24);\n" +
                        "#26=IFCCARTESIANPOINT((0.0,0.0,1298.34));\n" +
                        "#27=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#28=IFCAXIS2PLACEMENT3D(#26,#27,#9);\n" +
                        "#29=IFCAXIS1PLACEMENT(#7,#27);\n" +
                        "#30=IFCREVOLVEDAREASOLID(#25,#28,#29,6" +
                        ".283185307179586);\n" +
                        "#31=IFCCARTESIANPOINT((84.14,0.0));\n" +
                        "#32=IFCCARTESIANPOINT((0.0,649.17));\n" +
                        "#33=IFCPOLYLINE((#20,#31,#32));\n" +
                        "#34=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#33);\n" +
                        "#35=IFCREVOLVEDAREASOLID(#34,#28,#29,6" +
                        ".283185307179586);\n" +
                        "#36=IFCBOOLEANRESULT(.DIFFERENCE.,#30,#35);\n" +
                        "#37=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#38=IFCAXIS2PLACEMENT3D(#7,#37,#9);\n" +
                        "#39=IFCREVOLVEDAREASOLID(#34,#38,#29,6" +
                        ".283185307179586);\n" +
                        "#40=IFCCARTESIANPOINT((84.04,0.0));\n" +
                        "#41=IFCCARTESIANPOINT((0.0,648.3984644639886));\n" +
                        "#42=IFCPOLYLINE((#20,#40,#41));\n" +
                        "#43=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#42);\n" +
                        "#44=IFCREVOLVEDAREASOLID(#43,#38,#29,6" +
                        ".283185307179586);\n" +
                        "#45=IFCBOOLEANRESULT(.DIFFERENCE.,#39,#44);\n" +
                        "#46=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#47=IFCCIRCLEPROFILEDEF(.AREA.,$,#46,84.14);\n" +
                        "#48=IFCCARTESIANPOINT((0.0,0.0,649.17));\n" +
                        "#49=IFCAXIS2PLACEMENT3D(#48,$,$);\n" +
                        "#50=IFCREVOLVEDAREASOLID(#47,#49,#29,3" +
                        ".141592653589793);\n" +
                        "#51=IFCBOOLEANRESULT(.UNION.,#50,#36);\n" +
                        "#52=IFCREVOLVEDAREASOLID(#43,#28,#29,6" +
                        ".283185307179586);\n" +
                        "#53=IFCBOOLEANRESULT(.DIFFERENCE.,#35,#52);\n" +
                        "#54=IFCREVOLVEDAREASOLID(#25,#38,#29,6" +
                        ".283185307179586);\n" +
                        "#55=IFCBOOLEANRESULT(.DIFFERENCE.,#54,#39);\n" +
                        "#56=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#36," +
                        "#45,#51,#53,#55));\n" +
                        "#57=IFCPRODUCTDEFINITIONSHAPE($,$,(#56));\n" +
                        "#58=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Valve','{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBVALVOLE\",\\X\\0A  \"ADESCR\" : " +
                        "\"VL5292556\",\\X\\0A  \"IACCESSO\" : " +
                        "\"0-Sconoscuito\"," +
                        "\\X\\0A  \"NDIAMETRO\" : 168.28,\\X\\0A  " +
                        "\"ITIPO_VALVOLA\" :" +
                        " \"1-VALVE_STD\",\\X\\0A  \"ITIPO_COLL\" : " +
                        "\"0-COLL_FLANGIATO\",\\X\\0A  \"IOVOLANTINO\" : " +
                        "\"0-Alto\"," +
                        "\\X\\0A  \"ASCHEDULE\" : \"120\",\\X\\0A  \"ISTATO\"" +
                        " : " +
                        "\"0-APERTA\",\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\"," +
                        "\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"AGRUPPO_SCH\" : " +
                        "\"03D\"\\X\\0A}',$,#19,#57,.PRODUCT.,$);\n" +
                        "#59=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#58),#18);\n" +
                        "#60=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one ThreeWaysValve.
     */
    @Test
    public void convert_ThreeWaysValve() throws IOException {
        URL file = r.getResourcesWithLeafName("threewaysvalve.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/threewaysvalve.ifc";
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
                        "#21=IFCCARTESIANPOINT((84.14,0.0));\n" +
                        "#22=IFCCARTESIANPOINT((0.0,649.17));\n" +
                        "#23=IFCPOLYLINE((#20,#21,#22));\n" +
                        "#24=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#23);\n" +
                        "#25=IFCCARTESIANPOINT((0.0,0.0,1298.34));\n" +
                        "#26=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#25,#26,#9);\n" +
                        "#28=IFCAXIS1PLACEMENT(#7,#26);\n" +
                        "#29=IFCREVOLVEDAREASOLID(#24,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#30=IFCCARTESIANPOINT((84.04,0.0));\n" +
                        "#31=IFCCARTESIANPOINT((0.0,648.3984644639886));\n" +
                        "#32=IFCPOLYLINE((#20,#30,#31));\n" +
                        "#33=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#32);\n" +
                        "#34=IFCREVOLVEDAREASOLID(#33,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#35=IFCBOOLEANRESULT(.DIFFERENCE.,#29,#34);\n" +
                        "#36=IFCDIRECTION((1.0,0.0));\n" +
                        "#37=IFCAXIS2PLACEMENT2D(#20,#36);\n" +
                        "#38=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,100.0);\n" +
                        "#39=IFCEXTRUDEDAREASOLID(#38,#10,#8,30.0);\n" +
                        "#40=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#41=IFCAXIS2PLACEMENT3D(#7,#40,#9);\n" +
                        "#42=IFCREVOLVEDAREASOLID(#24,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#43=IFCBOOLEANRESULT(.DIFFERENCE.,#39,#42);\n" +
                        "#44=IFCREVOLVEDAREASOLID(#33,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#45=IFCBOOLEANRESULT(.DIFFERENCE.,#42,#44);\n" +
                        "#46=IFCCARTESIANPOINT((0.0,0.0,1268.34));\n" +
                        "#47=IFCAXIS2PLACEMENT3D(#46,#8,#9);\n" +
                        "#48=IFCEXTRUDEDAREASOLID(#38,#47,#8,30.0);\n" +
                        "#49=IFCBOOLEANRESULT(.DIFFERENCE.,#48,#29);\n" +
                        "#50=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,84.14);\n" +
                        "#51=IFCCARTESIANPOINT((0.0,0.0,649.17));\n" +
                        "#52=IFCAXIS2PLACEMENT3D(#51,#40,#9);\n" +
                        "#53=IFCREVOLVEDAREASOLID(#50,#52,#28,3" +
                        ".141592653589793);\n" +
                        "#54=IFCBOOLEANRESULT(.UNION.,#42,#29);\n" +
                        "#55=IFCBOOLEANRESULT(.DIFFERENCE.,#53,#54);\n" +
                        "#56=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#35," +
                        "#43,#45," + "#49,#55));\n" +
                        "#57=IFCPRODUCTDEFINITIONSHAPE($,$,(#56));\n" +
                        "#58=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Valve','{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBVALVOLE\",\\X\\0A  \"ADESCR\" : " +
                        "\"VL5292556\",\\X\\0A  \"IACCESSO\" : " +
                        "\"0-Sconoscuito\"," +
                        "\\X\\0A  \"NDIAMETRO\" : 168.28,\\X\\0A  " +
                        "\"ITIPO_VALVOLA\" :" +
                        " \"1-VALVE_STD\",\\X\\0A  \"ITIPO_COLL\" : " +
                        "\"0-COLL_FLANGIATO\",\\X\\0A  \"IOVOLANTINO\" : " +
                        "\"0-Alto\"," +
                        "\\X\\0A  \"ASCHEDULE\" : \"120\",\\X\\0A  \"ISTATO\"" +
                        " : " +
                        "\"0-APERTA\",\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\"," +
                        "\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"AGRUPPO_SCH\" : " +
                        "\"03D\"\\X\\0A}',$,#19,#57,.PRODUCT.,$);\n" +
                        "#59=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#58),#18);\n" +
                        "#60=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one FourWaysValve.
     */
    @Test
    public void convert_FourWaysValve() throws IOException {
        URL file = r.getResourcesWithLeafName("fourwaysvalve.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/fourwaysvalve.ifc";
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
                        "#21=IFCCARTESIANPOINT((84.14,0.0));\n" +
                        "#22=IFCCARTESIANPOINT((0.0,649.17));\n" +
                        "#23=IFCPOLYLINE((#20,#21,#22));\n" +
                        "#24=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#23);\n" +
                        "#25=IFCCARTESIANPOINT((0.0,0.0,1298.34));\n" +
                        "#26=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#25,#26,#9);\n" +
                        "#28=IFCAXIS1PLACEMENT(#7,#26);\n" +
                        "#29=IFCREVOLVEDAREASOLID(#24,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#30=IFCCARTESIANPOINT((84.04,0.0));\n" +
                        "#31=IFCCARTESIANPOINT((0.0,648.3984644639886));\n" +
                        "#32=IFCPOLYLINE((#20,#30,#31));\n" +
                        "#33=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#32);\n" +
                        "#34=IFCREVOLVEDAREASOLID(#33,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#35=IFCBOOLEANRESULT(.DIFFERENCE.,#29,#34);\n" +
                        "#36=IFCDIRECTION((1.0,0.0));\n" +
                        "#37=IFCAXIS2PLACEMENT2D(#20,#36);\n" +
                        "#38=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,100.0);\n" +
                        "#39=IFCEXTRUDEDAREASOLID(#38,#10,#8,30.0);\n" +
                        "#40=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#41=IFCAXIS2PLACEMENT3D(#7,#40,#9);\n" +
                        "#42=IFCREVOLVEDAREASOLID(#24,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#43=IFCBOOLEANRESULT(.DIFFERENCE.,#39,#42);\n" +
                        "#44=IFCREVOLVEDAREASOLID(#33,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#45=IFCBOOLEANRESULT(.DIFFERENCE.,#42,#44);\n" +
                        "#46=IFCCARTESIANPOINT((0.0,0.0,1268.34));\n" +
                        "#47=IFCAXIS2PLACEMENT3D(#46,#8,#9);\n" +
                        "#48=IFCEXTRUDEDAREASOLID(#38,#47,#8,30.0);\n" +
                        "#49=IFCBOOLEANRESULT(.DIFFERENCE.,#48,#29);\n" +
                        "#50=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,84.14);\n" +
                        "#51=IFCCARTESIANPOINT((0.0,0.0,649.17));\n" +
                        "#52=IFCAXIS2PLACEMENT3D(#51,#40,#9);\n" +
                        "#53=IFCREVOLVEDAREASOLID(#50,#52,#28,3" +
                        ".141592653589793);\n" +
                        "#54=IFCBOOLEANRESULT(.UNION.,#42,#29);\n" +
                        "#55=IFCBOOLEANRESULT(.DIFFERENCE.,#53,#54);\n" +
                        "#56=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#35," +
                        "#43,#45," + "#49,#55));\n" +
                        "#57=IFCPRODUCTDEFINITIONSHAPE($,$,(#56));\n" +
                        "#58=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Valve','{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBVALVOLE\",\\X\\0A  \"ADESCR\" : " +
                        "\"VL5292556\",\\X\\0A  \"IACCESSO\" : " +
                        "\"0-Sconoscuito\"," +
                        "\\X\\0A  \"NDIAMETRO\" : 168.28,\\X\\0A  " +
                        "\"ITIPO_VALVOLA\" :" +
                        " \"1-VALVE_STD\",\\X\\0A  \"ITIPO_COLL\" : " +
                        "\"0-COLL_FLANGIATO\",\\X\\0A  \"IOVOLANTINO\" : " +
                        "\"0-Alto\"," +
                        "\\X\\0A  \"ASCHEDULE\" : \"120\",\\X\\0A  \"ISTATO\"" +
                        " : " +
                        "\"0-APERTA\",\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\"," +
                        "\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"AGRUPPO_SCH\" : " +
                        "\"03D\"\\X\\0A}',$,#19,#57,.PRODUCT.,$);\n" +
                        "#59=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#58),#18);\n" +
                        "#60=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one OrthoValve.
     */
    @Test
    public void convert_OrthoValve() throws IOException {
        URL file =
                r.getResourcesWithLeafName("orthovalve.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/orthovalve.ifc";
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
                        "#21=IFCCARTESIANPOINT((84.14,0.0));\n" +
                        "#22=IFCCARTESIANPOINT((0.0,649.17));\n" +
                        "#23=IFCPOLYLINE((#20,#21,#22));\n" +
                        "#24=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#23);\n" +
                        "#25=IFCCARTESIANPOINT((0.0,0.0,1298.34));\n" +
                        "#26=IFCDIRECTION((0.0,1.0,0.0));\n" +
                        "#27=IFCAXIS2PLACEMENT3D(#25,#26,#9);\n" +
                        "#28=IFCAXIS1PLACEMENT(#7,#26);\n" +
                        "#29=IFCREVOLVEDAREASOLID(#24,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#30=IFCCARTESIANPOINT((84.04,0.0));\n" +
                        "#31=IFCCARTESIANPOINT((0.0,648.3984644639886));\n" +
                        "#32=IFCPOLYLINE((#20,#30,#31));\n" +
                        "#33=IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#32);\n" +
                        "#34=IFCREVOLVEDAREASOLID(#33,#27,#28,6" +
                        ".283185307179586);\n" +
                        "#35=IFCBOOLEANRESULT(.DIFFERENCE.,#29,#34);\n" +
                        "#36=IFCDIRECTION((1.0,0.0));\n" +
                        "#37=IFCAXIS2PLACEMENT2D(#20,#36);\n" +
                        "#38=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,100.0);\n" +
                        "#39=IFCEXTRUDEDAREASOLID(#38,#10,#8,30.0);\n" +
                        "#40=IFCDIRECTION((0.0,-1.0,0.0));\n" +
                        "#41=IFCAXIS2PLACEMENT3D(#7,#40,#9);\n" +
                        "#42=IFCREVOLVEDAREASOLID(#24,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#43=IFCBOOLEANRESULT(.DIFFERENCE.,#39,#42);\n" +
                        "#44=IFCREVOLVEDAREASOLID(#33,#41,#28,6" +
                        ".283185307179586);\n" +
                        "#45=IFCBOOLEANRESULT(.DIFFERENCE.,#42,#44);\n" +
                        "#46=IFCCARTESIANPOINT((0.0,0.0,1268.34));\n" +
                        "#47=IFCAXIS2PLACEMENT3D(#46,#8,#9);\n" +
                        "#48=IFCEXTRUDEDAREASOLID(#38,#47,#8,30.0);\n" +
                        "#49=IFCBOOLEANRESULT(.DIFFERENCE.,#48,#29);\n" +
                        "#50=IFCCIRCLEPROFILEDEF(.AREA.,$,#37,84.14);\n" +
                        "#51=IFCCARTESIANPOINT((0.0,0.0,649.17));\n" +
                        "#52=IFCAXIS2PLACEMENT3D(#51,#40,#9);\n" +
                        "#53=IFCREVOLVEDAREASOLID(#50,#52,#28,3" +
                        ".141592653589793);\n" +
                        "#54=IFCBOOLEANRESULT(.UNION.,#42,#29);\n" +
                        "#55=IFCBOOLEANRESULT(.DIFFERENCE.,#53,#54);\n" +
                        "#56=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#35," +
                        "#43,#45," + "#49,#55));\n" +
                        "#57=IFCPRODUCTDEFINITIONSHAPE($,$,(#56));\n" +
                        "#58=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Valve','{\\X\\0A  " +
                        "\"CATEGORY\" : \"TUBVALVOLE\",\\X\\0A  \"ADESCR\" : " +
                        "\"VL5292556\",\\X\\0A  \"IACCESSO\" : " +
                        "\"0-Sconoscuito\"," +
                        "\\X\\0A  \"NDIAMETRO\" : 168.28,\\X\\0A  " +
                        "\"ITIPO_VALVOLA\" :" +
                        " \"1-VALVE_STD\",\\X\\0A  \"ITIPO_COLL\" : " +
                        "\"0-COLL_FLANGIATO\",\\X\\0A  \"IOVOLANTINO\" : " +
                        "\"0-Alto\"," +
                        "\\X\\0A  \"ASCHEDULE\" : \"120\",\\X\\0A  \"ISTATO\"" +
                        " : " +
                        "\"0-APERTA\",\\X\\0A  \"ITIPO_STD\" : \"0-ANSI\"," +
                        "\\X\\0A  " +
                        "\"ITIPO_FAMSPEC\" : \"\",\\X\\0A  \"AGRUPPO_SCH\" : " +
                        "\"03D\"\\X\\0A}',$,#19,#57,.PRODUCT.,$);\n" +
                        "#59=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() +
                        ",#6,'Site to geometries link',$," + "(#58),#18);\n" +
                        "#60=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() +
                        ",#6,'Project to" + " site link',$,#17,(#18));\n" +
                        "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Ring.
     */
    @Test
    public void convert_Ring() throws IOException {
        URL file = r.getResourcesWithLeafName("ring.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/ring.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#21,298.0,94" +
                        ".80000000000001);\n" +
                        "#23=IFCEXTRUDEDAREASOLID(#22,#10,#8,3.0);\n" +
                        "#24=IFCSHAPEREPRESENTATION(#11,'Body','SweptSolid'," +
                        "(#23));\n" +
                        "#25=IFCPRODUCTDEFINITIONSHAPE($,$,(#24));\n" +
                        "#26=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Ring'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"PIASTRECIRC\",\\X\\0A  " +
                        "\"ADESCR\" : \"guarnizione\",\\X\\0A  " +
                        "\"NDIAMETROINT\" : 406.4,\\X\\0A  \"NSPESS\" : 3," +
                        "\\X\\0A  \"NDIAMETROEST\" : 596\\X\\0A}',$,#19,#25," +
                        ".PRODUCT.,$);\n" +
                        "#27=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#26),#18);\n" + "#28=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Tee.
     */
    @Test
    public void convert_Tee() throws IOException {
        URL file = r.getResourcesWithLeafName("tee.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/tee.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one
     * RectangularShell.
     */
    @Test
    public void convert_RectangularShell() throws IOException {
        URL file = r.getResourcesWithLeafName("rectangularshell.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/rectangularshell.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one
     * RectangularBlind.
     */
    @Test
    public void convert_RectangularBlind() throws IOException {
        URL file = r.getResourcesWithLeafName("rectangularblind.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/rectangularblind.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one
     * RectangularFlange.
     */
    @Test
    public void convert_RectangularFlange() throws IOException {
        URL file =
                r.getResourcesWithLeafName("rectangularflange.eywa").getURLs()
                        .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/rectangularflange.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one
     * RectangularEndplate.
     */
    @Test
    public void convert_RectangularEndplate() throws IOException {
        URL file =
                r.getResourcesWithLeafName("rectangularendplate.eywa").getURLs()
                        .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/rectangularendplate.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one
     * RectangularPlate.
     */
    @Test
    public void convert_RectangularPlate() throws IOException {
        URL file = r.getResourcesWithLeafName("rectangularplate.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/rectangularplate.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Dielectric.
     */
    @Test
    public void convert_Dielectric() throws IOException {
        URL file =
                r.getResourcesWithLeafName("dielectric.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/dielectric.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Beam.
     */
    @Test
    public void convert_Beam() throws IOException {
        URL file = r.getResourcesWithLeafName("beam.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/beam.ifc";
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
                        "#21=IFCAXIS2PLACEMENT2D(#20,$);\n" +
                        "#22=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,10.67);\n" +
                        "#23=IFCDIRECTION((0.0,0.8775825618903728,0" +
                        ".479425538604203));\n" +
                        "#24=IFCAXIS2PLACEMENT3D(#7,#23,#9);\n" +
                        "#25=IFCEXTRUDEDAREASOLID(#22,#24,#8,25.4);\n" +
                        "#26=IFCCIRCLEPROFILEDEF(.AREA.,$,#21,5.89);\n" +
                        "#27=IFCEXTRUDEDAREASOLID(#26,#24,#8,25.4);\n" +
                        "#28=IFCBOOLEANRESULT(.DIFFERENCE.,#25,#27);\n" +
                        "#29=IFCDIRECTION((0.0,0.0,-1.0));\n" +
                        "#30=IFCEXTRUDEDAREASOLID(#22,#10,#29,25.4);\n" +
                        "#31=IFCEXTRUDEDAREASOLID(#22,#10,#8,25.4);\n" +
                        "#32=IFCBOOLEANRESULT(.UNION.,#30,#31);\n" +
                        "#33=IFCBOOLEANRESULT(.DIFFERENCE.,#28,#32);\n" +
                        "#34=IFCEXTRUDEDAREASOLID(#26,#10,#29,25.4);\n" +
                        "#35=IFCEXTRUDEDAREASOLID(#26,#10,#8,25.4);\n" +
                        "#36=IFCBOOLEANRESULT(.UNION.,#34,#35);\n" +
                        "#37=IFCBOOLEANRESULT(.DIFFERENCE.,#32,#36);\n" +
                        "#38=IFCBOOLEANRESULT(.DIFFERENCE.,#37,#27);\n" +
                        "#39=IFCSHAPEREPRESENTATION(#11,'Body','CSG',(#33," +
                        "#38));\n" +
                        "#40=IFCPRODUCTDEFINITIONSHAPE($,$,(#39));\n" +
                        "#41=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6,'Tee'," +
                        "'{\\X\\0A  \"CATEGORY\" : \"TUBDIRAME\",\\X\\0A  " +
                        "\"ADESCR\" : \"New\",\\X\\0A  \"NDIAMETRO1\" : 21" +
                        ".34,\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"ASCHEDULE\" : \"160\",\\X\\0A  \"ITIPO_STD\" : " +
                        "\"0-ANSI\",\\X\\0A  \"ITIPO_FAMSPEC\" : \"\",\\X\\0A" +
                        "  \"AGRUPPO_SCH\" : \"03D\",\\X\\0A  \"NSPESS\" : 4" +
                        ".78,\\X\\0A  \"NDIAMETRO2\" : 21.34,\\X\\0A  " +
                        "\"NLUNGHEZZA0\" : 50.7999999999943,\\X\\0A  " +
                        "\"NLUNGHEZZA1\" : 25.3999999999942\\X\\0A}',$,#19," +
                        "#40,.PRODUCT.,$);\n" +
                        "#42=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#41),#18);\n" + "#43=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#17,(#18));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }
}
