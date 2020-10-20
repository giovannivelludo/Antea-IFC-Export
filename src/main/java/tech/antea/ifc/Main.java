/*
 * This file is part of Antea IFC Export.
 *
 * Author: Federico Russo
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Antea srl
 * Modifications Copyright (c) 2020 Giovanni Velludo
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
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.imc.persistence.po.eytukan.EywaRoot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This is a dummy class, just to have an entry point.
 */
public class Main {
    private static final String HELP_MSG =
            "Usage: mvn exec:java -Dexec.mainClass=tech.antea.ifc.Main -Dexec" +
                    ".args=\"-i inputfile -o outputfile\"";
    private static String inputFilePath;
    private static String outputFilePath;

    public static void main(String[] args) throws IOException {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    @SuppressWarnings("UnusedAssignment")
    private static int run(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Not enough arguments.");
            System.out.println(HELP_MSG);
            return 1;
        }
        if (args[0].equals("--help") || args[0].equals("-help") ||
                args[0].equals("-h") || args[0].equals("help")) {
            System.out.println(HELP_MSG);
            return 0;
        }
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            switch (arg) {
                case "-i":
                    inputFilePath = args[++i];
                    break;
                case "-o":
                    outputFilePath = args[++i];
                    break;
                default:
                    System.err.println("Invalid option \"" + arg + "\".");
                    System.out.println(HELP_MSG);
                    return 1;
            }
        }
        if (inputFilePath == null) {
            System.err.println("Missing input file.");
            System.out.println(HELP_MSG);
            return 1;
        }
        if (outputFilePath == null) {
            System.err.println("Missing output file.");
            System.out.println(HELP_MSG);
            return 1;
        }

        File eywaFile = new File(inputFilePath);
        ObjectMapper objectMapper = new ObjectMapper();
        EywaRoot eywaRoot;
        try {
            eywaRoot = objectMapper.readValue(eywaFile, EywaRoot.class);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("Given input file " + eywaFile.getName() +
                                       " is not a valid .eywa file.");
            return 1;
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + e.getMessage());
            return 1;
        }
        objectMapper = null;

        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader director = new EywaReader(builder);
        director.convert(eywaRoot);
        IfcProject result = builder.getResult();

        // deleting references to objects that are no longer needed, so they
        // can get garbage collected if needed
        director = null;
        builder = null;
        eywaRoot = null;

        EywaToIfcConverter.writeToFile(result, outputFilePath);
        return 0;
    }
}
