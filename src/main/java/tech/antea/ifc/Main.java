package tech.antea.ifc;

import com.buildingsmart.tech.ifc.IfcActorResource.IfcOrganization;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPerson;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPersonAndOrganization;
import com.buildingsmart.tech.ifc.IfcGeometryResource.IfcAxis2Placement3D;
import com.buildingsmart.tech.ifc.IfcGeometryResource.IfcCartesianPoint;
import com.buildingsmart.tech.ifc.IfcGeometryResource.IfcDirection;
import com.buildingsmart.tech.ifc.IfcKernel.IfcProject;
import com.buildingsmart.tech.ifc.IfcRepresentationResource.IfcGeometricRepresentationContext;
import com.buildingsmart.tech.ifc.IfcRepresentationResource.IfcRepresentationContext;
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

        IfcGeometricRepresentationContext reprContext =
                new IfcGeometricRepresentationContext();
        reprContext.setContextIdentifier("Plan");
        reprContext.setContextType("Model");
        reprContext.setCoordinateSpaceDimension(3);
        reprContext.setPrecision(1.E-5);
        IfcAxis2Placement3D worldCoordSys = new IfcAxis2Placement3D();
        worldCoordSys.setLocation(new IfcCartesianPoint(new Double[]{0.0, 0.0, 0.0}));
        // TODO: edit library and add check on the size of the array passed in the constructor of IfcCartesianPoint and IfcDirection?
        worldCoordSys.setAxis(new IfcDirection(new Double[]{0.0, 0.0, 1.0}));
        worldCoordSys.setRefDirection(new IfcDirection(new Double[]{1.0, 0.0, 0.0}));
        reprContext.setWorldCoordinateSystem(worldCoordSys);
        reprContext.setTrueNorth(new IfcDirection(new Double[]{0.0, 1.0, 0.0}));
        Set<IfcRepresentationContext> reprContexts = new HashSet<>();
        reprContexts.add(reprContext);

        project.setRepresentationContexts(reprContexts);
//        project.setUnitsInContext();

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
