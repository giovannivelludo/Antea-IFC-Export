package tech.antea.ifc;

import com.buildingsmart.tech.ifc.IfcKernel.IfcProject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        IfcProject project = new IfcProject();
        String filePath = "/home/giovanni/Documents/tirocinio/ifc/prove" +
                "/cylinder-test.ifcXML";
        writeModelToXML(project, filePath);
    }

    /**
     * Writes the given {@link IfcProject} to the given filePath using the
     * ifcXML format.
     *
     * @param project  the {@link IfcProject} to write.
     * @param filePath the path to the file in which to write.
     */
    private static void writeModelToXML(IfcProject project, String filePath) {
        try {
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            String xmlString;
            xmlString = xmlMapper.writeValueAsString(project);

            File xmlOutput = new File(filePath);
            FileWriter fileWriter = new FileWriter(xmlOutput);
            fileWriter.write(xmlString);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
