package tech.antea.ifc;

import com.buildingsmart.tech.ifc.IfcActorResource.IfcOrganization;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPerson;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPersonAndOrganization;
import com.buildingsmart.tech.ifc.IfcGeometricConstraintResource.IfcLocalPlacement;
import com.buildingsmart.tech.ifc.IfcGeometricModelResource.IfcExtrudedAreaSolid;
import com.buildingsmart.tech.ifc.IfcGeometryResource.*;
import com.buildingsmart.tech.ifc.IfcKernel.IfcObjectDefinition;
import com.buildingsmart.tech.ifc.IfcKernel.IfcProduct;
import com.buildingsmart.tech.ifc.IfcKernel.IfcProject;
import com.buildingsmart.tech.ifc.IfcKernel.IfcRelAggregates;
import com.buildingsmart.tech.ifc.IfcMeasureResource.*;
import com.buildingsmart.tech.ifc.IfcPresentationAppearanceResource.*;
import com.buildingsmart.tech.ifc.IfcProductExtension.*;
import com.buildingsmart.tech.ifc.IfcProfileResource.IfcCircleProfileDef;
import com.buildingsmart.tech.ifc.IfcProfileResource.IfcProfileTypeEnum;
import com.buildingsmart.tech.ifc.IfcRepresentationResource.*;
import com.buildingsmart.tech.ifc.IfcSharedBldgElements.IfcWall;
import com.buildingsmart.tech.ifc.IfcUtilityResource.IfcApplication;
import com.buildingsmart.tech.ifc.IfcUtilityResource.IfcChangeActionEnum;
import com.buildingsmart.tech.ifc.IfcUtilityResource.IfcOwnerHistory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) {
        IfcProject project = new IfcProject();
        String filePath = "./ifc-out/cylinder-test.ifcXML";

        // ------- Creating an empty project -------

        project.setGlobalId("0NZgqcQj5AlvVyooSuXX4r");
        // TODO: implement algorithm for GlobalId generation as described in
        //  the IFC specification

        IfcPerson thePerson = new IfcPerson();
        thePerson.setGivenName("John");
        thePerson.setFamilyName("Doe");

        IfcOrganization theOrganization = new IfcOrganization();
        theOrganization.setIdentification("Antea");
        theOrganization.setName("Antea S.R.L.");

        IfcPersonAndOrganization owningUser = new IfcPersonAndOrganization();
        owningUser.setThePerson(thePerson);
        owningUser.setTheOrganization(theOrganization);

        IfcApplication owningApplication = new IfcApplication();
        owningApplication.setApplicationDeveloper(theOrganization);
        owningApplication.setApplicationFullName("AnteaIFC 0.1");
        owningApplication.setApplicationIdentifier("AnteaIFC");
        owningApplication.setVersion("0.1");

        IfcOwnerHistory ownerHistory = new IfcOwnerHistory();
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        int creationTime = (int) (calendar.getTimeInMillis() / 1000); // seconds
        ownerHistory.setOwningUser(owningUser);
        ownerHistory.setOwningApplication(owningApplication);
        ownerHistory.setChangeAction(IfcChangeActionEnum.NOCHANGE);
        ownerHistory.setCreationDate(creationTime);

        project.setOwnerHistory(ownerHistory);
        project.setName("cylinder-test");

        IfcAxis2Placement3D worldCoordSys = new IfcAxis2Placement3D();
        worldCoordSys.setLocation(new IfcCartesianPoint(new Double[]{0., 0.,
                0.}));
        worldCoordSys.setAxis(new IfcDirection(new Double[]{0., 0., 1.}));
        worldCoordSys.setRefDirection(new IfcDirection(new Double[]{1., 0.,
                0.}));

        IfcGeometricRepresentationContext reprContext =
                new IfcGeometricRepresentationContext();
        reprContext.setContextIdentifier("Plan");
        reprContext.setContextType("Model");
        reprContext.setCoordinateSpaceDimension(3);
        reprContext.setPrecision(1.E-5);
        reprContext.setWorldCoordinateSystem(worldCoordSys);
        reprContext.setTrueNorth(new IfcDirection(new Double[]{0., 1., 0.}));

        Set<IfcRepresentationContext> reprContextsSet = new HashSet<>();
        reprContextsSet.add(reprContext);
        // TODO: check if every HashSet used in this file works properly,
        //  since I did't override equals() and hashCode() in the types I used

        project.setRepresentationContexts(reprContextsSet);

        IfcUnit[] units = new IfcUnit[4];
        IfcDimensionalExponents metreDimensions =
                new IfcDimensionalExponents(1, 0, 0, 0, 0, 0, 0);
        // TODO: implement function IfcDimensionsForSiUnit in IfcSIUnit IFC.JAVA
        units[0] = new IfcSIUnit(metreDimensions, IfcUnitEnum.LENGTHUNIT,
                IfcSIUnitName.METRE);
        IfcDimensionalExponents squareMetreDimensions =
                new IfcDimensionalExponents(2, 0, 0, 0, 0, 0, 0);
        units[1] = new IfcSIUnit(squareMetreDimensions, IfcUnitEnum.AREAUNIT,
                IfcSIUnitName.SQUARE_METRE);
        IfcDimensionalExponents cubeMetreDimensions =
                new IfcDimensionalExponents(3, 0, 0, 0, 0, 0, 0);
        units[2] = new IfcSIUnit(cubeMetreDimensions, IfcUnitEnum.VOLUMEUNIT,
                IfcSIUnitName.CUBIC_METRE);
        IfcDimensionalExponents radianDimensions =
                new IfcDimensionalExponents(0, 0, 0, 0, 0, 0, 0);
        IfcSIUnit radian = new IfcSIUnit(radianDimensions,
                IfcUnitEnum.PLANEANGLEUNIT, IfcSIUnitName.RADIAN);
        IfcValue conversionFactor =
                new IfcPositivePlaneAngleMeasure(0.017453292519943295);
        IfcMeasureWithUnit measureDegreesWithRadian =
                new IfcMeasureWithUnit(conversionFactor, radian);
        units[3] = new IfcConversionBasedUnit(radianDimensions,
                IfcUnitEnum.PLANEANGLEUNIT, "DEGREE",
                measureDegreesWithRadian);
        IfcUnitAssignment unitsInContext = new IfcUnitAssignment(units);
        project.setUnitsInContext(unitsInContext);

        // ------- Creating a cylinder -------

        IfcAxis2Placement2D circlePosition =
                new IfcAxis2Placement2D(
                        new IfcCartesianPoint(new Double[]{0., 0.}));
        circlePosition.setRefDirection(new IfcDirection(new Double[]{1., 0.}));
        IfcCircleProfileDef circle =
                new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                        new IfcPositiveLengthMeasure(0.1));
        // TODO: edit all constructors that require objects such as
        //  IfcCartesianPoint, IfcPositiveLengthMeasure, IfcDirection,
        //  IfcNormalisedRatioMeasure, etc.
        //  to make them accept doubles or arrays of doubles as parameters
        circle.setPosition(circlePosition);
        IfcAxis2Placement3D cylinderPosition = new IfcAxis2Placement3D();
        cylinderPosition.setLocation(new IfcCartesianPoint(
                new Double[]{-1.4210854715202E-17, -2.73641172593403E-18, 0.}));
        cylinderPosition.setAxis(new IfcDirection(new Double[]{0., 0., 1.}));
        cylinderPosition.setRefDirection(
                new IfcDirection(new Double[]{1., 0., 0.}));
        IfcExtrudedAreaSolid cylinder = new IfcExtrudedAreaSolid(circle,
                new IfcDirection(new Double[]{0., 0., 1.}),
                new IfcPositiveLengthMeasure(0.1));
        cylinder.setPosition(cylinderPosition);

        // ------- Painting the cylinder -------

        IfcNormalisedRatioMeasure colorIntensity =
                new IfcNormalisedRatioMeasure(1);
        IfcColourRgb cylinderColour = new IfcColourRgb(colorIntensity,
                colorIntensity, colorIntensity);
        IfcSurfaceStyleRendering cylinderRendering =
                new IfcSurfaceStyleRendering(cylinderColour,
                        IfcReflectanceMethodEnum.FLAT);
        IfcSurfaceStyle cylinderSurfaceStyle =
                new IfcSurfaceStyle(IfcSurfaceSide.BOTH,
                        new IfcSurfaceStyleElementSelect[]{cylinderRendering});
        IfcPresentationStyleAssignment styleAssignment =
                new IfcPresentationStyleAssignment(
                        new IfcPresentationStyleSelect[]{cylinderSurfaceStyle});
        IfcStyledItem styledCylinder =
                new IfcStyledItem(
                        new IfcStyleAssignmentSelect[]{styleAssignment});
        styledCylinder.setItem(cylinder);
        Set<IfcStyledItem> cylinderStyler = new HashSet<>();
        cylinderStyler.add(styledCylinder);
        cylinder.setStyledByItem(cylinderStyler);

        // ------- Creating a wall with the shape of the cylinder we just
        // made -------

        IfcLocalPlacement wallPlacement = new IfcLocalPlacement(worldCoordSys);
        IfcShapeRepresentation cylinderShapeRepr =
                new IfcShapeRepresentation(reprContext,
                        new IfcRepresentationItem[]{cylinder});
        cylinderShapeRepr.setRepresentationIdentifier("Body");
        cylinderShapeRepr.setRepresentationType("SweptSolid");
        IfcProductDefinitionShape cylinderDefinitionShape =
                new IfcProductDefinitionShape(
                        new IfcRepresentation[]{cylinderShapeRepr});
        IfcWall wall = new IfcWall("2KcxKeVfqHwhb6N5zdz5Bw");
        // TODO: use algorithmically generated globalIds
        wall.setOwnerHistory(ownerHistory);
        wall.setName("Wall");
        wall.setDescription("");
        wall.setObjectPlacement(wallPlacement);
        wall.setRepresentation(cylinderDefinitionShape);

        // ------- Putting the cylinder-shaped wall into the project -------

        IfcSite site = new IfcSite("2KdG88VfqHwfDCN5zdz5Bw");
        site.setOwnerHistory(ownerHistory);
        site.setName("Default Site");
        site.setDescription("");
        site.setCompositionType(IfcElementCompositionEnum.ELEMENT);
        IfcRelAggregates projectLink = new IfcRelAggregates(
                "2KdG89VfqHweGDN5zdz5Bw", project,
                new IfcObjectDefinition[]{site});
        projectLink.setOwnerHistory(ownerHistory);
        projectLink.setName("ProjectLink");
        projectLink.setDescription("");
        IfcBuilding building = new IfcBuilding("2KdHMSVfqHwfiJN5zdz5Bw");
        building.setOwnerHistory(ownerHistory);
        building.setName("Default Building");
        building.setDescription("");
        building.setCompositionType(IfcElementCompositionEnum.ELEMENT);
        IfcRelAggregates siteLink = new IfcRelAggregates(
                "2KdHMTVfqHwePlN5zdz5Bw", site,
                new IfcObjectDefinition[]{building});
        siteLink.setOwnerHistory(ownerHistory);
        siteLink.setName("SiteLink");
        siteLink.setDescription("");
        IfcBuildingStorey storey = new IfcBuildingStorey(
                "2KdHMUVfqHwg4XN5zdz5Bw");
        storey.setOwnerHistory(ownerHistory);
        storey.setName("Default Storey");
        storey.setDescription("");
        storey.setCompositionType(IfcElementCompositionEnum.ELEMENT);
        IfcRelAggregates buildingLink = new IfcRelAggregates(
                "2KdHMVVfqHwhFMN5zdz5Bw", building,
                new IfcObjectDefinition[]{storey});
        buildingLink.setOwnerHistory(ownerHistory);
        buildingLink.setName("BuildingLink");
        buildingLink.setDescription("");
        IfcRelContainedInSpatialStructure storeyLink =
                new IfcRelContainedInSpatialStructure("2KdIamVfqHwf$aN5zdz5Bw",
                        new IfcProduct[]{wall}, storey);
        storeyLink.setOwnerHistory(ownerHistory);
        storeyLink.setName("StoreyLink");
        storeyLink.setDescription("");

        // ------- Linking each relation to its relating object -------

        Set<IfcRelAggregates> projectDecomposer = new HashSet<>();
        projectDecomposer.add(projectLink);
        project.setIsDecomposedBy(projectDecomposer);
        Set<IfcRelAggregates> siteDecomposer = new HashSet<>();
        siteDecomposer.add(siteLink);
        site.setIsDecomposedBy(siteDecomposer);
        Set<IfcRelAggregates> buildingDecomposer = new HashSet<>();
        buildingDecomposer.add(buildingLink);
        building.setIsDecomposedBy(buildingDecomposer);
        Set<IfcRelContainedInSpatialStructure> storeyElements = new HashSet<>();
        storeyElements.add(storeyLink);
        storey.setContainsElements(storeyElements);

        writeModelToXML(project, filePath);
    }

    /**
     * Writes the given {@link IfcProject} to the given filePath using the
     * ifcXML format.
     *
     * @param project  the {@link IfcProject} to write.
     * @param filePath the path to the file in which to write.
     * @throws IllegalArgumentException if {@code filePath} is null or empty.
     */
    private static void writeModelToXML(IfcProject project, String filePath) {
        try {
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            String xmlString;
            xmlString = xmlMapper.writeValueAsString(project);

            String directoryPath = null;
            if (null != filePath && filePath.length() > 0) {
                int endIndex = filePath.lastIndexOf("/");
                if (endIndex != -1) {
                    directoryPath = filePath.substring(0, endIndex);
                }
            }
            if (directoryPath == null)
                throw new IllegalArgumentException("filePath is null or empty");
            //noinspection ResultOfMethodCallIgnored
            new File(directoryPath).mkdirs();

            File xmlOutput = new File(filePath);
            FileWriter fileWriter = new FileWriter(xmlOutput);
            fileWriter.write(xmlString);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
