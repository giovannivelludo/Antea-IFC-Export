package tech.antea.ifc;

import com.buildingsmart.tech.ifc.IfcActorResource.IfcOrganization;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPerson;
import com.buildingsmart.tech.ifc.IfcActorResource.IfcPersonAndOrganization;
import com.buildingsmart.tech.ifc.IfcKernel.IfcProject;
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
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) {
        IfcProject project = new IfcProject();
        String filePath = "./ifc-out/cylinder-test.ifcXML";

        IfcPerson johnDoe = new IfcPerson();
        johnDoe.setGivenName("John");
        johnDoe.setFamilyName("Doe");

        IfcOrganization antea = new IfcOrganization();
        antea.setIdentification("Antea");
        antea.setName("Antea Holding S.R.L.");

        IfcPersonAndOrganization owningUser = new IfcPersonAndOrganization();
        owningUser.setThePerson(johnDoe);
        owningUser.setTheOrganization(antea);

        IfcApplication owningApplication = new IfcApplication();
        owningApplication.setApplicationDeveloper(antea);
        owningApplication.setApplicationFullName("AnteaIFC 0.1");
        owningApplication.setApplicationIdentifier("AnteaIFC");
        owningApplication.setVersion("0.1");

        IfcOwnerHistory ownerHistory = new IfcOwnerHistory();
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        int creationTime = (int) (calendar.getTimeInMillis() / 1000);
        ownerHistory.setOwningUser(owningUser);
        ownerHistory.setOwningApplication(owningApplication);
        ownerHistory.setChangeAction(IfcChangeActionEnum.NOCHANGE);
        ownerHistory.setCreationDate(creationTime);

        project.setGlobalId("0NZgqcQj5AlvVyooSuXX4r");
        // TODO: implement algorithm for GlobalId creation as described in
        //  the IFC specification
        project.setOwnerHistory(ownerHistory);
        project.setName("cylinder-test");
        //project.setUnitsInContext();
        // TODO: add method IfcContext.setRepresentationContexts() to the IFC
        //  .JAVA library

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
