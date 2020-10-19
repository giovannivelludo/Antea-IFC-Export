/*
 * This file is part of Antea IFC Export.
 *
 * Author: Giovanni Velludo
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Antea srl
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import it.imc.persistence.po.eytukan.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class EywaReaderTest {
    /**
     * Tests the conversion of an empty EywaRoot.
     */
    @Test(expected = IllegalArgumentException.class)
    public void convert_emptyEywaRoot() {
        EywaRoot eywaRoot = new EywaRoot();
        EywaToIfcConverter builder = new EywaToIfcConverter();
        EywaReader reader = new EywaReader(builder);
        reader.convert(eywaRoot);
    }

    /**
     * Tests whether Eywa objects are serialized in the correct order, using a
     * dummy EywaConverter.
     */
    @Test
    public void convert() throws IOException {
        ScanResult r = new ClassGraph()
                .whitelistPackages(EywaToIfcConverterTest.EYWA_RESOURCES_PACKAGE)
                .scan();
        URL file =
                r.getResourcesWithLeafName("051200TA001.eywa").getURLs().get(0);
        ObjectMapper objectMapper = new ObjectMapper();
        EywaRoot eywaRoot = objectMapper.readValue(file, EywaRoot.class);

        DummyEywaConverter builder = new DummyEywaConverter();
        EywaReader director = new EywaReader(builder);
        director.convert(eywaRoot);
        String result = builder.getResult();

        String expectedResult =
                // for some reason the imId of the first empty is not parsed
                "added hints;\n" + "added Empty with imId null;\n" +
                        "added Endplate with imId 1169185472;\n" +
                        "added TankShell with imId 1169185473;\n" +
                        "added Shell with imId 1169185474;\n" +
                        "added Nozzle with imId 1169185475;\n" +
                        "added Shell with imId 1169185476;\n" +
                        "added Nozzle with imId 1169185477;\n" +
                        "added Shell with imId 1169185478;\n" +
                        "added Nozzle with imId 1169185479;\n" +
                        "added Shell with imId 1169185480;\n" +
                        "added Nozzle with imId 1169185481;\n" +
                        "added Shell with imId 1169185482;\n" +
                        "added Nozzle with imId 1169185483;\n" +
                        "added Blind with imId 1169185484;\n" +
                        "added TankShell with imId 1169185485;\n" +
                        "added Shell with imId 1169185486;\n" +
                        "added Nozzle with imId 1169185487;\n" +
                        "added Shell with imId 1169185488;\n" +
                        "added Nozzle with imId 1169185489;\n" +
                        "added Shell with imId 1169185490;\n" +
                        "added Nozzle with imId 1169185491;\n" +
                        "added Shell with imId 1169185492;\n" +
                        "added Nozzle with imId 1169185493;\n" +
                        "added Shell with imId 1169185494;\n" +
                        "added Nozzle with imId 1169185495;\n" +
                        "added Shell with imId 1169185496;\n" +
                        "added Nozzle with imId 1169185497;\n" +
                        "added Shell with imId 1169185498;\n" +
                        "added Nozzle with imId 1169185499;\n" +
                        "added Endplate with imId 1169185500.";

        Assert.assertEquals(expectedResult, result);
    }

    private static class DummyEywaConverter implements EywaConverter {
        private final StringBuilder history = new StringBuilder();

        /**
         * @return The result of the conversion.
         *
         * @throws ConversionException If an error occurs during conversion.
         */
        @Override
        public String getResult() {
            return history.replace(history.length() - 2, history.length(), ".")
                    .toString();
        }

        /**
         * Call this method to use EywaRoot's hints in the conversion (if
         * possible).
         *
         * @param hints {@link EywaRoot#getHints()}
         * @throws ConversionException If an error occurs during conversion.
         */
        @Override
        public void addHints(Map<String, Object> hints) {
            history.append("added hints;\n");
        }

        private void log(Primitive obj) {
            history.append("added ").append(obj.getClass().getSimpleName())
                    .append(" with imId ").append(obj.getLegacyId())
                    .append(";\n");
        }

        /**
         * @param obj The {@link Beam} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Beam obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Blind} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Blind obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Box} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Box obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Collar} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Collar obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Curve} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Curve obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Dielectric} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Dielectric obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Dish} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Dish obj) {
            log(obj);
        }

        /**
         * @param obj The {@link DualExpansionJoint} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(DualExpansionJoint obj) {
            log(obj);
        }

        /**
         * @param obj The {@link EccentricCone} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(EccentricCone obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Empty} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Empty obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Endplate} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Endplate obj) {
            log(obj);
        }

        /**
         * @param obj The {@link ExpansionJoint} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(ExpansionJoint obj) {
            log(obj);
        }

        /**
         * @param obj The {@link FaceSet} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(FaceSet obj) {
            log(obj);
        }

        /**
         * @param obj The {@link FourWaysValve} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(FourWaysValve obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Instrument} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Instrument obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Ladder} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Ladder obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Mesh} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Mesh obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Nozzle} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Nozzle obj) {
            log(obj);
        }

        /**
         * @param obj The {@link OrthoValve} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(OrthoValve obj) {
            log(obj);
        }

        /**
         * @param obj The {@link RectangularBlind} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(RectangularBlind obj) {
            log(obj);
        }

        /**
         * @param obj The {@link RectangularEndplate} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(RectangularEndplate obj) {
            log(obj);
        }

        /**
         * @param obj The {@link RectangularFlange} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(RectangularFlange obj) {
            log(obj);
        }

        /**
         * @param obj The {@link RectangularPlate} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(RectangularPlate obj) {
            log(obj);
        }

        /**
         * @param obj The {@link RectangularShell} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(RectangularShell obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Ring} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Ring obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Shell} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Shell obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Sphere} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Sphere obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Stair} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Stair obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Sweep} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Sweep obj) {
            log(obj);
        }

        /**
         * @param obj The {@link TankShell} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(TankShell obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Tee} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Tee obj) {
            log(obj);
        }

        /**
         * @param obj The {@link ThreeWaysValve} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(ThreeWaysValve obj) {
            log(obj);
        }

        /**
         * @param obj The {@link Valve} to convert.
         * @throws NullPointerException If {@code obj} is null.
         * @throws ConversionException  If an error occurs during conversion.
         */
        @Override
        public void addObject(Valve obj) {
            log(obj);
        }
    }
}
