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

    /**
     * Tests the conversion of an EywaRoot containing only one TankShell.
     */
    @Test
    public void convert_TankShell() throws IOException {
        URL file =
                r.getResourcesWithLeafName("tankshell.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/tankshell.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#19=IFCAXIS2PLACEMENT2D(#18,$);\n" +
                        "#20=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#19,581.0,6" +
                        ".0);\n" + "#21=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#22=IFCEXTRUDEDAREASOLID(#20,#8,#21,1980.0);\n" +
                        "#23=IFCSHAPEREPRESENTATION(#9,'Body','SweptSolid'," +
                        "(#22));\n" +
                        "#24=IFCPRODUCTDEFINITIONSHAPE($,$,(#23));\n" +
                        "#25=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'TankShell','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPVIROLE\",\\X\\0A  \"ADESCR\" : \"Virola\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 1162,\\X\\0A  \"NSPESS\" : 6," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 1980\\X\\0A}',$,#17," +
                        "#24,.PRODUCT.,$);\n" +
                        "#26=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#25),#16);\n" + "#27=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Box.
     */
    @Test
    public void convert_Box() throws IOException {
        URL file = r.getResourcesWithLeafName("box.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/box.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#19=IFCAXIS2PLACEMENT2D(#18,$);\n" +
                        "#20=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#19,581.0,6" +
                        ".0);\n" + "#21=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#22=IFCEXTRUDEDAREASOLID(#20,#8,#21,1980.0);\n" +
                        "#23=IFCSHAPEREPRESENTATION(#9,'Body','SweptSolid'," +
                        "(#22));\n" +
                        "#24=IFCPRODUCTDEFINITIONSHAPE($,$,(#23));\n" +
                        "#25=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'TankShell','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPVIROLE\",\\X\\0A  \"ADESCR\" : \"Virola\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 1162,\\X\\0A  \"NSPESS\" : 6," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 1980\\X\\0A}',$,#17," +
                        "#24,.PRODUCT.,$);\n" +
                        "#26=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#25),#16);\n" + "#27=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Collar.
     */
    @Test
    public void convert_Collar() throws IOException {
        URL file = r.getResourcesWithLeafName("collar.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/collar.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#19=IFCAXIS2PLACEMENT2D(#18,$);\n" +
                        "#20=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#19,581.0,6" +
                        ".0);\n" + "#21=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#22=IFCEXTRUDEDAREASOLID(#20,#8,#21,1980.0);\n" +
                        "#23=IFCSHAPEREPRESENTATION(#9,'Body','SweptSolid'," +
                        "(#22));\n" +
                        "#24=IFCPRODUCTDEFINITIONSHAPE($,$,(#23));\n" +
                        "#25=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'TankShell','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPVIROLE\",\\X\\0A  \"ADESCR\" : \"Virola\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 1162,\\X\\0A  \"NSPESS\" : 6," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 1980\\X\\0A}',$,#17," +
                        "#24,.PRODUCT.,$);\n" +
                        "#26=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#25),#16);\n" + "#27=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one FaceSet.
     */
    @Test
    public void convert_FaceSet() throws IOException {
        URL file = r.getResourcesWithLeafName("faceset.eywa").getURLs().get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/faceset.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#19=IFCAXIS2PLACEMENT2D(#18,$);\n" +
                        "#20=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#19,581.0,6" +
                        ".0);\n" + "#21=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#22=IFCEXTRUDEDAREASOLID(#20,#8,#21,1980.0);\n" +
                        "#23=IFCSHAPEREPRESENTATION(#9,'Body','SweptSolid'," +
                        "(#22));\n" +
                        "#24=IFCPRODUCTDEFINITIONSHAPE($,$,(#23));\n" +
                        "#25=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'TankShell','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPVIROLE\",\\X\\0A  \"ADESCR\" : \"Virola\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 1162,\\X\\0A  \"NSPESS\" : 6," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 1980\\X\\0A}',$,#17," +
                        "#24,.PRODUCT.,$);\n" +
                        "#26=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#25),#16);\n" + "#27=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one Mesh.
     */
    @Test
    public void convert_Mesh() throws IOException {
        URL file = r.getResourcesWithLeafName("mesh.eywa").getURLs().get(0);
        //FIXME: in mesh.eywa, vertices 0 and 12 are the same (line 18 and
        // 54), how is this possible? They're used in the first polygon,
        // which makes it a line, this violates the UNIQUE constraint in
        // IfcPolyLoop
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/mesh.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((0.0,0.0));\n" +
                        "#19=IFCAXIS2PLACEMENT2D(#18,$);\n" +
                        "#20=IFCCIRCLEHOLLOWPROFILEDEF(.AREA.,$,#19,581.0,6" +
                        ".0);\n" + "#21=IFCDIRECTION((0.0,0.0,1.0));\n" +
                        "#22=IFCEXTRUDEDAREASOLID(#20,#8,#21,1980.0);\n" +
                        "#23=IFCSHAPEREPRESENTATION(#9,'Body','SweptSolid'," +
                        "(#22));\n" +
                        "#24=IFCPRODUCTDEFINITIONSHAPE($,$,(#23));\n" +
                        "#25=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'TankShell','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPVIROLE\",\\X\\0A  \"ADESCR\" : \"Virola\"," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 3,\\X\\0A  " +
                        "\"NDIAMETRO\" : 1162,\\X\\0A  \"NSPESS\" : 6," +
                        "\\X\\0A  \"NLUNGHEZZA\" : 1980\\X\\0A}',$,#17," +
                        "#24,.PRODUCT.,$);\n" +
                        "#26=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#25),#16);\n" + "#27=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }

    /**
     * Tests the conversion of an EywaRoot containing only one EccentricCone.
     */
    @Test
    public void convert_EccentricCone() throws IOException {
        URL file = r.getResourcesWithLeafName("eccentriccone.eywa").getURLs()
                .get(0);
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
        IfcProject result = builder.getResult();
        String filePath = "./ifc-out/eccentriccone.ifc";
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
                        "#8=IFCAXIS2PLACEMENT3D(#7,$,$);\n" +
                        "#9=IFCGEOMETRICREPRESENTATIONCONTEXT('Plan','Model'," +
                        "3,1.0E-8,#8,$);\n" +
                        "#10=IFCSIUNIT(*,.LENGTHUNIT.,.MILLI.,.METRE.);\n" +
                        "#11=IFCSIUNIT(*,.AREAUNIT.,.MILLI.,.SQUARE_METRE.);" +
                        "\n" +
                        "#12=IFCSIUNIT(*,.VOLUMEUNIT.,.MILLI.,.CUBIC_METRE.);" +
                        "\n" +
                        "#13=IFCSIUNIT(*,.PLANEANGLEUNIT.,$,.RADIAN.);\n" +
                        "#14=IFCUNITASSIGNMENT((#10,#11,#12,#13));\n" +
                        "#15=IFCPROJECT(" + result.getGlobalId().serialize() +
                        ",#6," + "'05-190-0-VL-001',$,$,$,$,(#9),#14);\n" +
                        "#16=IFCSITE(" + ifcSite.getGlobalId().serialize() +
                        ",#6,$,$,$,$,$,$," + ".COMPLEX.,$,$,$,$,$);\n" +
                        "#17=IFCLOCALPLACEMENT($,#8);\n" +
                        "#18=IFCCARTESIANPOINT((203.2,50.80000000000001,508" +
                        ".0));\n" +
                        "#19=IFCCARTESIANPOINT((199.29556897793643,90" +
                        ".44235343367727,508.0));\n" +
                        "#20=IFCCARTESIANPOINT((187.73232100629346,128" +
                        ".56127345658626,508.0));\n" +
                        "#21=IFCCARTESIANPOINT((168.9546252198772,163" +
                        ".69187134958315,508.0));\n" +
                        "#22=IFCCARTESIANPOINT((143.68409793710646,194" +
                        ".48409793710644,508.0));\n" +
                        "#23=IFCCARTESIANPOINT((112.89187134958318,219" +
                        ".7546252198772,508.0));\n" +
                        "#24=IFCCARTESIANPOINT((77.76127345658625,238" +
                        ".53232100629347,508.0));\n" +
                        "#25=IFCCARTESIANPOINT((39.64235343367727,250" +
                        ".09556897793644,508.0));\n" +
                        "#26=IFCCARTESIANPOINT((1.2442411479337107E-14,254.0," +
                        "508.0));\n" +
                        "#27=IFCCARTESIANPOINT((-39.64235343367724,250" +
                        ".09556897793644,508.0));\n" +
                        "#28=IFCCARTESIANPOINT((-77.76127345658622,238" +
                        ".53232100629347,508.0));\n" +
                        "#29=IFCCARTESIANPOINT((-112.89187134958311,219" +
                        ".75462521987723,508.0));\n" +
                        "#30=IFCCARTESIANPOINT((-143.68409793710643,194" +
                        ".48409793710647,508.0));\n" +
                        "#31=IFCCARTESIANPOINT((-168.95462521987722,163" +
                        ".69187134958315,508.0));\n" +
                        "#32=IFCCARTESIANPOINT((-187.73232100629346,128" +
                        ".56127345658626,508.0));\n" +
                        "#33=IFCCARTESIANPOINT((-199.29556897793643,90" +
                        ".44235343367734,508.0));\n" +
                        "#34=IFCCARTESIANPOINT((-203.2,50.80000000000004,508" +
                        ".0));\n" +
                        "#35=IFCCARTESIANPOINT((-199.29556897793643,11" +
                        ".157646566322732,508.0));\n" +
                        "#36=IFCCARTESIANPOINT((-187.7323210062935,-26" +
                        ".9612734565862,508.0));\n" +
                        "#37=IFCCARTESIANPOINT((-168.95462521987722,-62" +
                        ".0918713495831,508.0));\n" +
                        "#38=IFCCARTESIANPOINT((-143.6840979371065,-92" +
                        ".88409793710642,508.0));\n" +
                        "#39=IFCCARTESIANPOINT((-112.89187134958316,-118" +
                        ".15462521987718,508.0));\n" +
                        "#40=IFCCARTESIANPOINT((-77.76127345658635,-136" +
                        ".9323210062934,508.0));\n" +
                        "#41=IFCCARTESIANPOINT((-39.64235343367734,-148" +
                        ".4955689779364,508.0));\n" +
                        "#42=IFCCARTESIANPOINT((-3.732723443801132E-14,-152" +
                        ".39999999999998,508.0));\n" +
                        "#43=IFCCARTESIANPOINT((39.64235343367727,-148" +
                        ".49556897793641,508.0));\n" +
                        "#44=IFCCARTESIANPOINT((77.76127345658628,-136" +
                        ".93232100629342,508.0));\n" +
                        "#45=IFCCARTESIANPOINT((112.89187134958308,-118" +
                        ".1546252198772,508.0));\n" +
                        "#46=IFCCARTESIANPOINT((143.6840979371064,-92" +
                        ".88409793710647,508.0));\n" +
                        "#47=IFCCARTESIANPOINT((168.9546252198772,-62" +
                        ".091871349583144,508.0));\n" +
                        "#48=IFCCARTESIANPOINT((187.7323210062934,-26" +
                        ".961273456586355,508.0));\n" +
                        "#49=IFCCARTESIANPOINT((199.2955689779364,11" +
                        ".157646566322654,508.0));\n" +
                        "#50=IFCPOLYLOOP((#18,#19,#20,#21,#22,#23,#24,#25," +
                        "#26,#27,#28,#29,#30,#31,#32,#33,#34,#35,#36,#37," +
                        "#38,#39,#40,#41,#42,#43,#44,#45,#46,#47,#48,#49));" +
                        "\n" + "#51=IFCFACEBOUND(#50,.T.);\n" +
                        "#52=IFCFACE((#51));\n" +
                        "#53=IFCCARTESIANPOINT((254.0,0.0,0.0));\n" +
                        "#54=IFCCARTESIANPOINT((249.11946122242054,49" +
                        ".552941792096576,0.0));\n" +
                        "#55=IFCPOLYLOOP((#53,#54,#19,#18));\n" +
                        "#56=IFCFACEBOUND(#55,.T.);\n" +
                        "#57=IFCFACE((#56));\n" +
                        "#58=IFCCARTESIANPOINT((234.66540125786682,97" +
                        ".2015918207328,0.0));\n" +
                        "#59=IFCCARTESIANPOINT((211.1932815248465,141" +
                        ".11483918697894,0.0));\n" +
                        "#60=IFCCARTESIANPOINT((179.60512242138307,179" +
                        ".60512242138304,0.0));\n" +
                        "#61=IFCCARTESIANPOINT((141.11483918697897,211" +
                        ".1932815248465,0.0));\n" +
                        "#62=IFCCARTESIANPOINT((97.20159182073282,234" +
                        ".66540125786682,0.0));\n" +
                        "#63=IFCCARTESIANPOINT((49.5529417920966,249" +
                        ".11946122242054,0.0));\n" +
                        "#64=IFCCARTESIANPOINT((1.5553014349171386E-14,254.0," +
                        "0.0));\n" +
                        "#65=IFCCARTESIANPOINT((-49.55294179209656,249" +
                        ".11946122242054,0.0));\n" +
                        "#66=IFCCARTESIANPOINT((-97.20159182073279,234" +
                        ".66540125786682,0.0));\n" +
                        "#67=IFCCARTESIANPOINT((-141.11483918697888,211" +
                        ".19328152484655,0.0));\n" +
                        "#68=IFCCARTESIANPOINT((-179.60512242138304,179" +
                        ".60512242138307,0.0));\n" +
                        "#69=IFCCARTESIANPOINT((-211.19328152484653,141" +
                        ".11483918697894,0.0));\n" +
                        "#70=IFCCARTESIANPOINT((-234.66540125786682,97" +
                        ".20159182073283,0.0));\n" +
                        "#71=IFCCARTESIANPOINT((-249.11946122242054,49" +
                        ".55294179209667,0.0));\n" +
                        "#72=IFCCARTESIANPOINT((-254.0,3.110602869834277E-14," +
                        "0.0));\n" +
                        "#73=IFCCARTESIANPOINT((-249.11946122242054,-49" +
                        ".552941792096604,0.0));\n" +
                        "#74=IFCCARTESIANPOINT((-234.66540125786685,-97" +
                        ".20159182073277,0.0));\n" +
                        "#75=IFCCARTESIANPOINT((-211.19328152484655,-141" +
                        ".11483918697888,0.0));\n" +
                        "#76=IFCCARTESIANPOINT((-179.6051224213831,-179" +
                        ".60512242138304,0.0));\n" +
                        "#77=IFCCARTESIANPOINT((-141.11483918697894,-211" +
                        ".1932815248465,0.0));\n" +
                        "#78=IFCCARTESIANPOINT((-97.20159182073294,-234" +
                        ".66540125786676,0.0));\n" +
                        "#79=IFCCARTESIANPOINT((-49.55294179209668,-249" +
                        ".1194612224205,0.0));\n" +
                        "#80=IFCCARTESIANPOINT((-4.6659043047514155E-14,-254" +
                        ".0,0.0));\n" +
                        "#81=IFCCARTESIANPOINT((49.55294179209659,-249" +
                        ".11946122242054,0.0));\n" +
                        "#82=IFCCARTESIANPOINT((97.20159182073286,-234" +
                        ".6654012578668,0.0));\n" +
                        "#83=IFCCARTESIANPOINT((141.11483918697886,-211" +
                        ".19328152484655,0.0));\n" +
                        "#84=IFCCARTESIANPOINT((179.60512242138302,-179" +
                        ".6051224213831,0.0));\n" +
                        "#85=IFCCARTESIANPOINT((211.1932815248465,-141" +
                        ".11483918697894,0.0));\n" +
                        "#86=IFCCARTESIANPOINT((234.66540125786676,-97" +
                        ".20159182073296,0.0));\n" +
                        "#87=IFCCARTESIANPOINT((249.1194612224205,-49" +
                        ".552941792096696,0.0));\n" +
                        "#88=IFCPOLYLOOP((#53,#54,#58,#59,#60,#61,#62,#63," +
                        "#64,#65,#66,#67,#68,#69,#70,#71,#72,#73,#74,#75," +
                        "#76,#77,#78,#79,#80,#81,#82,#83,#84,#85,#86,#87));" +
                        "\n" + "#89=IFCFACEBOUND(#88,.T.);\n" +
                        "#90=IFCFACE((#89));\n" +
                        "#91=IFCPOLYLOOP((#63,#64,#26,#25));\n" +
                        "#92=IFCFACEBOUND(#91,.T.);\n" +
                        "#93=IFCFACE((#92));\n" +
                        "#94=IFCPOLYLOOP((#86,#87,#49,#48));\n" +
                        "#95=IFCFACEBOUND(#94,.T.);\n" +
                        "#96=IFCFACE((#95));\n" +
                        "#97=IFCPOLYLOOP((#60,#61,#23,#22));\n" +
                        "#98=IFCFACEBOUND(#97,.T.);\n" +
                        "#99=IFCFACE((#98));\n" +
                        "#100=IFCPOLYLOOP((#81,#82,#44,#43));\n" +
                        "#101=IFCFACEBOUND(#100,.T.);\n" +
                        "#102=IFCFACE((#101));\n" +
                        "#103=IFCPOLYLOOP((#64,#65,#27,#26));\n" +
                        "#104=IFCFACEBOUND(#103,.T.);\n" +
                        "#105=IFCFACE((#104));\n" +
                        "#106=IFCPOLYLOOP((#54,#58,#20,#19));\n" +
                        "#107=IFCFACEBOUND(#106,.T.);\n" +
                        "#108=IFCFACE((#107));\n" +
                        "#109=IFCPOLYLOOP((#87,#49,#18,#53));\n" +
                        "#110=IFCFACEBOUND(#109,.T.);\n" +
                        "#111=IFCFACE((#110));\n" +
                        "#112=IFCPOLYLOOP((#58,#59,#21,#20));\n" +
                        "#113=IFCFACEBOUND(#112,.T.);\n" +
                        "#114=IFCFACE((#113));\n" +
                        "#115=IFCPOLYLOOP((#75,#76,#38,#37));\n" +
                        "#116=IFCFACEBOUND(#115,.T.);\n" +
                        "#117=IFCFACE((#116));\n" +
                        "#118=IFCPOLYLOOP((#80,#81,#43,#42));\n" +
                        "#119=IFCFACEBOUND(#118,.T.);\n" +
                        "#120=IFCFACE((#119));\n" +
                        "#121=IFCPOLYLOOP((#79,#80,#42,#41));\n" +
                        "#122=IFCFACEBOUND(#121,.T.);\n" +
                        "#123=IFCFACE((#122));\n" +
                        "#124=IFCPOLYLOOP((#66,#67,#29,#28));\n" +
                        "#125=IFCFACEBOUND(#124,.T.);\n" +
                        "#126=IFCFACE((#125));\n" +
                        "#127=IFCPOLYLOOP((#85,#86,#48,#47));\n" +
                        "#128=IFCFACEBOUND(#127,.T.);\n" +
                        "#129=IFCFACE((#128));\n" +
                        "#130=IFCPOLYLOOP((#83,#84,#46,#45));\n" +
                        "#131=IFCFACEBOUND(#130,.T.);\n" +
                        "#132=IFCFACE((#131));\n" +
                        "#133=IFCPOLYLOOP((#84,#85,#47,#46));\n" +
                        "#134=IFCFACEBOUND(#133,.T.);\n" +
                        "#135=IFCFACE((#134));\n" +
                        "#136=IFCPOLYLOOP((#76,#77,#39,#38));\n" +
                        "#137=IFCFACEBOUND(#136,.T.);\n" +
                        "#138=IFCFACE((#137));\n" +
                        "#139=IFCPOLYLOOP((#70,#71,#33,#32));\n" +
                        "#140=IFCFACEBOUND(#139,.T.);\n" +
                        "#141=IFCFACE((#140));\n" +
                        "#142=IFCPOLYLOOP((#67,#68,#30,#29));\n" +
                        "#143=IFCFACEBOUND(#142,.T.);\n" +
                        "#144=IFCFACE((#143));\n" +
                        "#145=IFCPOLYLOOP((#65,#66,#28,#27));\n" +
                        "#146=IFCFACEBOUND(#145,.T.);\n" +
                        "#147=IFCFACE((#146));\n" +
                        "#148=IFCPOLYLOOP((#72,#73,#35,#34));\n" +
                        "#149=IFCFACEBOUND(#148,.T.);\n" +
                        "#150=IFCFACE((#149));\n" +
                        "#151=IFCPOLYLOOP((#78,#79,#41,#40));\n" +
                        "#152=IFCFACEBOUND(#151,.T.);\n" +
                        "#153=IFCFACE((#152));\n" +
                        "#154=IFCPOLYLOOP((#61,#62,#24,#23));\n" +
                        "#155=IFCFACEBOUND(#154,.T.);\n" +
                        "#156=IFCFACE((#155));\n" +
                        "#157=IFCPOLYLOOP((#69,#70,#32,#31));\n" +
                        "#158=IFCFACEBOUND(#157,.T.);\n" +
                        "#159=IFCFACE((#158));\n" +
                        "#160=IFCPOLYLOOP((#73,#74,#36,#35));\n" +
                        "#161=IFCFACEBOUND(#160,.T.);\n" +
                        "#162=IFCFACE((#161));\n" +
                        "#163=IFCPOLYLOOP((#59,#60,#22,#21));\n" +
                        "#164=IFCFACEBOUND(#163,.T.);\n" +
                        "#165=IFCFACE((#164));\n" +
                        "#166=IFCPOLYLOOP((#77,#78,#40,#39));\n" +
                        "#167=IFCFACEBOUND(#166,.T.);\n" +
                        "#168=IFCFACE((#167));\n" +
                        "#169=IFCPOLYLOOP((#68,#69,#31,#30));\n" +
                        "#170=IFCFACEBOUND(#169,.T.);\n" +
                        "#171=IFCFACE((#170));\n" +
                        "#172=IFCPOLYLOOP((#71,#72,#34,#33));\n" +
                        "#173=IFCFACEBOUND(#172,.T.);\n" +
                        "#174=IFCFACE((#173));\n" +
                        "#175=IFCPOLYLOOP((#62,#63,#25,#24));\n" +
                        "#176=IFCFACEBOUND(#175,.T.);\n" +
                        "#177=IFCFACE((#176));\n" +
                        "#178=IFCPOLYLOOP((#82,#83,#45,#44));\n" +
                        "#179=IFCFACEBOUND(#178,.T.);\n" +
                        "#180=IFCFACE((#179));\n" +
                        "#181=IFCPOLYLOOP((#74,#75,#37,#36));\n" +
                        "#182=IFCFACEBOUND(#181,.T.);\n" +
                        "#183=IFCFACE((#182));\n" +
                        "#184=IFCCLOSEDSHELL((#52,#57,#90,#93,#96,#99,#102," +
                        "#105,#108,#111,#114,#117,#120,#123,#126,#129,#132," +
                        "#135,#138,#141,#144,#147,#150,#153,#156,#159,#162," +
                        "#165,#168,#171,#174,#177,#180,#183));\n" +
                        "#185=IFCFACETEDBREP(#184);\n" +
                        "#186=IFCCARTESIANPOINT((-125.22553052261833,-187" +
                        ".4132506129937,0.0));\n" +
                        "#187=IFCCARTESIANPOINT((-86.25684565509137,-208" +
                        ".24244662804398,0.0));\n" +
                        "#188=IFCCARTESIANPOINT((-66.81652729094478,-110" +
                        ".50936637647061,508.0));\n" +
                        "#189=IFCCARTESIANPOINT((-97.00256268522254,-94" +
                        ".37459430802437,508.0));\n" +
                        "#190=IFCPOLYLOOP((#186,#187,#188,#189));\n" +
                        "#191=IFCFACEBOUND(#190,.T.);\n" +
                        "#192=IFCFACE((#191));\n" +
                        "#193=IFCCARTESIANPOINT((-225.4,2" +
 ".7603538852781343E-14,0.0));\n" +
                        "#194=IFCCARTESIANPOINT((-221.06900220288816,-43" +
                        ".97335858243533,0.0));\n" +
                        "#195=IFCCARTESIANPOINT((-171.24510995840402,16" +
 ".737229775983998,508.0));\n" +
                        "#196=IFCCARTESIANPOINT((-174.6,50.80000000000003,508" +
                        ".0));\n" +
                        "#197=IFCPOLYLOOP((#193,#194,#195,#196));\n" +
                        "#198=IFCFACEBOUND(#197,.T.);\n" +
                        "#199=IFCFACE((#198));\n" +
                        "#200=IFCCARTESIANPOINT((86.25684565509125,208" +
                        ".24244662804404,0.0));\n" +
                        "#201=IFCCARTESIANPOINT((43.973358582435324,221" +
                        ".06900220288816,0.0));\n" +
                        "#202=IFCCARTESIANPOINT((34.062770224016006,222" +
                        ".04510995840403,508.0));\n" +
                        "#203=IFCCARTESIANPOINT((66.81652729094468,212" +
                        ".10936637647066,508.0));\n" +
                        "#204=IFCPOLYLOOP((#200,#201,#202,#203));\n" +
                        "#205=IFCFACEBOUND(#204,.T.);\n" +
                        "#206=IFCFACE((#205));\n" +
                        "#207=IFCCARTESIANPOINT((-187.41325061299372,125" +
                        ".22553052261833,0.0));\n" +
                        "#208=IFCCARTESIANPOINT((-208.24244662804404,86" +
                        ".25684565509127,0.0));\n" +
                        "#209=IFCCARTESIANPOINT((-161.30936637647065,117" +
                        ".6165272909447,508.0));\n" +
                        "#210=IFCCARTESIANPOINT((-145.1745943080244,147" +
                        ".80256268522254,508.0));\n" +
                        "#211=IFCPOLYLOOP((#207,#208,#209,#210));\n" +
                        "#212=IFCFACEBOUND(#211,.T.);\n" +
                        "#213=IFCFACE((#212));\n" +
                        "#214=IFCCARTESIANPOINT((225.4,0.0,0.0));\n" +
                        "#215=IFCCARTESIANPOINT((221.06900220288816,43" +
                        ".97335858243531,0.0));\n" +
                        "#216=IFCCARTESIANPOINT((171.24510995840402,84" +
                        ".862770224016,508.0));\n" +
                        "#217=IFCCARTESIANPOINT((174.6,50.80000000000001,508" +
                        ".0));\n" +
                        "#218=IFCPOLYLOOP((#214,#215,#216,#217));\n" +
                        "#219=IFCFACEBOUND(#218,.T.);\n" +
                        "#220=IFCFACE((#219));\n" +
                        "#221=IFCCARTESIANPOINT((1.3801769426390671E-14,225" +
                        ".4,0.0));\n" +
                        "#222=IFCCARTESIANPOINT((-43.973358582435296,221" +
                        ".06900220288816,0.0));\n" +
                        "#223=IFCCARTESIANPOINT((-34.062770224015985,222" +
                        ".04510995840403,508.0));\n" +
                        "#224=IFCCARTESIANPOINT((1.0691166556556393E-14,225" +
                        ".4,508.0));\n" +
                        "#225=IFCPOLYLOOP((#221,#222,#223,#224));\n" +
                        "#226=IFCFACEBOUND(#225,.T.);\n" +
                        "#227=IFCFACE((#226));\n" +
                        "#228=IFCCARTESIANPOINT((187.4132506129937,-125" +
                        ".22553052261833,0.0));\n" +
                        "#229=IFCCARTESIANPOINT((208.24244662804398,-86" +
                        ".25684565509138,0.0));\n" +
                        "#230=IFCCARTESIANPOINT((161.30936637647062,-16" +
                        ".016527290944765,508.0));\n" +
                        "#231=IFCCARTESIANPOINT((145.17459430802438,-46" +
                        ".20256268522253,508.0));\n" +
                        "#232=IFCPOLYLOOP((#228,#229,#230,#231));\n" +
                        "#233=IFCFACEBOUND(#232,.T.);\n" +
                        "#234=IFCFACE((#233));\n" +
                        "#235=IFCCARTESIANPOINT((208.24244662804404,86" +
                        ".25684565509124,0.0));\n" +
                        "#236=IFCCARTESIANPOINT((187.4132506129937,125" +
                        ".22553052261833,0.0));\n" +
                        "#237=IFCCARTESIANPOINT((145.17459430802438,147" +
                        ".80256268522254,508.0));\n" +
                        "#238=IFCCARTESIANPOINT((161.30936637647065,117" +
                        ".61652729094469,508.0));\n" +
                        "#239=IFCPOLYLOOP((#235,#236,#237,#238));\n" +
                        "#240=IFCFACEBOUND(#239,.T.);\n" +
                        "#241=IFCFACE((#240));\n" +
                        "#242=IFCCARTESIANPOINT((-125.22553052261829,187" +
                        ".41325061299375,0.0));\n" +
                        "#243=IFCCARTESIANPOINT((-159.3818684794478,159" +
                        ".38186847944783,0.0));\n" +
                        "#244=IFCCARTESIANPOINT((-123.46084399517119,174" +
                        ".26084399517123,508.0));\n" +
                        "#245=IFCCARTESIANPOINT((-97.0025626852225,195" +
                        ".97459430802445,508.0));\n" +
                        "#246=IFCPOLYLOOP((#242,#243,#244,#245));\n" +
                        "#247=IFCFACEBOUND(#246,.T.);\n" +
                        "#248=IFCFACE((#247));\n" +
                        "#249=IFCCARTESIANPOINT((125.22553052261826,-187" +
                        ".41325061299375,0.0));\n" +
                        "#250=IFCCARTESIANPOINT((159.38186847944777,-159" +
                        ".38186847944786,0.0));\n" +
                        "#251=IFCCARTESIANPOINT((123.46084399517116,-72" +
                        ".6608439951712,508.0));\n" +
                        "#252=IFCCARTESIANPOINT((97.00256268522247,-94" +
                        ".37459430802443,508.0));\n" +
                        "#253=IFCPOLYLOOP((#249,#250,#251,#252));\n" +
                        "#254=IFCFACEBOUND(#253,.T.);\n" +
                        "#255=IFCFACE((#254));\n" +
                        "#256=IFCPOLYLOOP((#250,#228,#231,#251));\n" +
                        "#257=IFCFACEBOUND(#256,.T.);\n" +
                        "#258=IFCFACE((#257));\n" +
                        "#259=IFCCARTESIANPOINT((221.06900220288813,-43" +
                        ".97335858243542,0.0));\n" +
                        "#260=IFCCARTESIANPOINT((171.24510995840402,16" +
                        ".73722977598394,508.0));\n" +
                        "#261=IFCPOLYLOOP((#259,#260,#217,#214));\n" +
                        "#262=IFCFACEBOUND(#261,.T.);\n" +
                        "#263=IFCFACE((#262));\n" +
                        "#264=IFCCARTESIANPOINT((-221.06900220288816,43" +
                        ".97335858243539,0.0));\n" +
                        "#265=IFCCARTESIANPOINT((-171.24510995840402,84" +
                        ".86277022401606,508.0));\n" +
                        "#266=IFCPOLYLOOP((#208,#264,#265,#209));\n" +
                        "#267=IFCFACEBOUND(#266,.T.);\n" +
                        "#268=IFCFACE((#267));\n" +
                        "#269=IFCPOLYLOOP((#264,#193,#196,#265));\n" +
                        "#270=IFCFACEBOUND(#269,.T.);\n" +
                        "#271=IFCFACE((#270));\n" +
                        "#272=IFCCARTESIANPOINT((-4.140530827917201E-14,-225" +
                        ".4,0.0));\n" +
                        "#273=IFCCARTESIANPOINT((43.973358582435324,-221" +
                        ".06900220288816,0.0));\n" +
                        "#274=IFCCARTESIANPOINT((34.062770224016,-120" +
                        ".445109958404,508.0));\n" +
                        "#275=IFCCARTESIANPOINT((-3.2073499669669176E-14,-123" +
                        ".79999999999998,508.0));\n" +
                        "#276=IFCPOLYLOOP((#272,#273,#274,#275));\n" +
                        "#277=IFCFACEBOUND(#276,.T.);\n" +
                        "#278=IFCFACE((#277));\n" +
                        "#279=IFCCARTESIANPOINT((86.25684565509128,-208" +
                        ".242446628044,0.0));\n" +
                        "#280=IFCCARTESIANPOINT((66.8165272909447,-110" +
                        ".50936637647064,508.0));\n" +
                        "#281=IFCPOLYLOOP((#279,#249,#252,#280));\n" +
                        "#282=IFCFACEBOUND(#281,.T.);\n" +
                        "#283=IFCFACE((#282));\n" +
                        "#284=IFCCARTESIANPOINT((123.4608439951712,174" +
                        ".2608439951712,508.0));\n" +
                        "#285=IFCCARTESIANPOINT((97.00256268522256,195" +
                        ".9745943080244,508.0));\n" +
                        "#286=IFCCARTESIANPOINT((-66.81652729094466,212" +
                        ".10936637647066,508.0));\n" +
                        "#287=IFCCARTESIANPOINT((-161.30936637647068,-16" +
                        ".016527290944637,508.0));\n" +
                        "#288=IFCCARTESIANPOINT((-145.17459430802444,-46" +
                        ".20256268522249,508.0));\n" +
                        "#289=IFCCARTESIANPOINT((-123.46084399517122,-72" +
                        ".66084399517118,508.0));\n" +
                        "#290=IFCCARTESIANPOINT((-34.06277022401606,-120" +
 ".445109958404,508.0));\n" +
                        "#291=IFCPOLYLOOP((#217,#216,#238,#237,#284,#285," +
                        "#203,#202,#224,#223,#286,#245,#244,#210,#209,#265," +
                        "#196,#195,#287,#288,#289,#189,#188,#290,#275,#274," +
                        "#280,#252,#251,#231,#230,#260));\n" +
                        "#292=IFCFACEBOUND(#291,.T.);\n" +
                        "#293=IFCFACE((#292));\n" +
                        "#294=IFCCARTESIANPOINT((-187.41325061299375,-125" +
                        ".22553052261829,0.0));\n" +
                        "#295=IFCCARTESIANPOINT((-159.38186847944786,-159" +
                        ".3818684794478,0.0));\n" +
                        "#296=IFCPOLYLOOP((#294,#295,#289,#288));\n" +
                        "#297=IFCFACEBOUND(#296,.T.);\n" +
                        "#298=IFCFACE((#297));\n" +
                        "#299=IFCCARTESIANPOINT((-43.9733585824354,-221" +
                        ".06900220288813,0.0));\n" +
                        "#300=IFCPOLYLOOP((#299,#272,#275,#290));\n" +
                        "#301=IFCFACEBOUND(#300,.T.);\n" +
                        "#302=IFCFACE((#301));\n" +
                        "#303=IFCPOLYLOOP((#273,#279,#280,#274));\n" +
                        "#304=IFCFACEBOUND(#303,.T.);\n" +
                        "#305=IFCFACE((#304));\n" +
                        "#306=IFCCARTESIANPOINT((125.22553052261836,187" +
                        ".4132506129937,0.0));\n" +
                        "#307=IFCPOLYLOOP((#306,#200,#203,#285));\n" +
                        "#308=IFCFACEBOUND(#307,.T.);\n" +
                        "#309=IFCFACE((#308));\n" +
                        "#310=IFCCARTESIANPOINT((159.38186847944783,159" +
                        ".3818684794478,0.0));\n" +
                        "#311=IFCPOLYLOOP((#236,#310,#284,#237));\n" +
                        "#312=IFCFACEBOUND(#311,.T.);\n" +
                        "#313=IFCFACE((#312));\n" +
                        "#314=IFCCARTESIANPOINT((-86.25684565509123,208" +
                        ".24244662804404,0.0));\n" +
                        "#315=IFCPOLYLOOP((#222,#314,#286,#223));\n" +
                        "#316=IFCFACEBOUND(#315,.T.);\n" +
                        "#317=IFCFACE((#316));\n" +
                        "#318=IFCCARTESIANPOINT((-208.24244662804406,-86" +
                        ".25684565509121,0.0));\n" +
                        "#319=IFCPOLYLOOP((#214,#215,#235,#236,#310,#306," +
                        "#200,#201,#221,#222,#314,#242,#243,#207,#208,#264," +
                        "#193,#194,#318,#294,#295,#186,#187,#299,#272,#273," +
                        "#279,#249,#250,#228,#229,#259));\n" +
                        "#320=IFCFACEBOUND(#319,.T.);\n" +
                        "#321=IFCFACE((#320));\n" +
                        "#322=IFCPOLYLOOP((#194,#318,#287,#195));\n" +
                        "#323=IFCFACEBOUND(#322,.T.);\n" +
                        "#324=IFCFACE((#323));\n" +
                        "#325=IFCPOLYLOOP((#187,#299,#290,#188));\n" +
                        "#326=IFCFACEBOUND(#325,.T.);\n" +
                        "#327=IFCFACE((#326));\n" +
                        "#328=IFCPOLYLOOP((#215,#235,#238,#216));\n" +
                        "#329=IFCFACEBOUND(#328,.T.);\n" +
                        "#330=IFCFACE((#329));\n" +
                        "#331=IFCPOLYLOOP((#229,#259,#260,#230));\n" +
                        "#332=IFCFACEBOUND(#331,.T.);\n" +
                        "#333=IFCFACE((#332));\n" +
                        "#334=IFCPOLYLOOP((#314,#242,#245,#286));\n" +
                        "#335=IFCFACEBOUND(#334,.T.);\n" +
                        "#336=IFCFACE((#335));\n" +
                        "#337=IFCPOLYLOOP((#318,#294,#288,#287));\n" +
                        "#338=IFCFACEBOUND(#337,.T.);\n" +
                        "#339=IFCFACE((#338));\n" +
                        "#340=IFCPOLYLOOP((#243,#207,#210,#244));\n" +
                        "#341=IFCFACEBOUND(#340,.T.);\n" +
                        "#342=IFCFACE((#341));\n" +
                        "#343=IFCPOLYLOOP((#310,#306,#285,#284));\n" +
                        "#344=IFCFACEBOUND(#343,.T.);\n" +
                        "#345=IFCFACE((#344));\n" +
                        "#346=IFCPOLYLOOP((#201,#221,#224,#202));\n" +
                        "#347=IFCFACEBOUND(#346,.T.);\n" +
                        "#348=IFCFACE((#347));\n" +
                        "#349=IFCPOLYLOOP((#295,#186,#189,#289));\n" +
                        "#350=IFCFACEBOUND(#349,.T.);\n" +
                        "#351=IFCFACE((#350));\n" +
                        "#352=IFCCLOSEDSHELL((#192,#199,#206,#213,#220,#227," +
                        "#234,#241,#248,#255,#258,#263,#268,#271,#278,#283," +
                        "#293,#298,#302,#305,#309,#313,#317,#321,#324,#327," +
                        "#330,#333,#336,#339,#342,#345,#348,#351));\n" +
                        "#354=IFCBOOLEANRESULT(.DIFFERENCE.,#185,#353);\n" +
                        "#355=IFCSHAPEREPRESENTATION(#9,'Body','CSG',(#354));" +
                        "\n" + "#356=IFCPRODUCTDEFINITIONSHAPE($,$,(#355));\n" +
                        "#357=IFCPROXY(" + ifcProxy.getGlobalId().serialize() +
                        ",#6," + "'EccentricCone','{\\X\\0A  \"CATEGORY\" : " +
                        "\"APPCONIECC\",\\X\\0A  \"ADESCR\" : \"Cono " +
                        "eccentrico\",\\X\\0A  \"NDIAMETRO1\" : 508," +
                        "\\X\\0A  \"NCOR_ALLOW\" : 1,\\X\\0A  \"NSPESS\" " +
                        ": 28.6,\\X\\0A  \"NDIAMETRO2\" : 406" +
                        ".4\\X\\0A}',$,#17,#356,.PRODUCT.,$);\n" +
                        "#358=IFCRELCONTAINEDINSPATIALSTRUCTURE" + "(" +
                        ifcRelContainedInSpatialStructure.getGlobalId()
                                .serialize() + ",#6,'Site to geometries " +
                        "link',$,(#357),#16);\n" + "#359=IFCRELAGGREGATES(" +
                        ifcRelAggregates.getGlobalId().serialize() + ",#6," +
                        "'Project to site link',$,#15,(#16));\n" + "ENDSEC;\n";

        String ifcDataSection = getDataSection(filePath);
        Assert.assertEquals(expectedDataSection, ifcDataSection);
    }
}
