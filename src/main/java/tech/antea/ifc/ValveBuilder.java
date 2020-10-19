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

import buildingsmart.ifc.*;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.PI;

class ValveBuilder {
    private final ValveOutput[] outputs = new ValveOutput[4];
    private final double thickness;

    /**
     * @param thickness Thickness of the Valve.
     */
    public ValveBuilder(double thickness) {
        this.thickness = thickness;
    }

    /**
     * @param values The list to read.
     * @return The minimum value in the list. If the list contains all null
     * elements, null will be returned.
     * @throws NullPointerException If values is null.
     * @throws IllegalArgumentException If values is empty.
     */
    private static Double min(@NonNull List<Double> values) {
        if (values.size() == 0) {
            throw new IllegalArgumentException("values is empty");
        }
        int i = 0;
        Double min = null;
        while (i < values.size()) {
            Double current = values.get(i);
            if (current != null) {
                min = current;
                i++;
                break;
            }
            i++;
        }
        while (i < values.size()) {
            Double current = values.get(i);
            if (current != null && current < min) {
                min = current;
            }
            i++;
        }
        return min;
    }

    /**
     * Builds a valve output according to the given parameters.
     * @param position 0 indicates the bottom output, others follow
     * anticlockwise.
     * @param radius Radius of the output.
     * @param length Length of the output.
     * @param crownRadius Radius of the crown if the valve is flanged, 0
     * otherwise.
     * @param crownThickness Thickness of the crown if the valve is flanged, 0
     * otherwise.
     * @return The IfcRevolvedAreaSolid representing the output.
     * @throws NullPointerException If {@link #addBottomOutput(double, double,
     * double, double)} was not called before this
     * method (because all kinds of valves have the
     * bottom output).
     */
    private IfcRevolvedAreaSolid buildValveOutput(byte position, double radius, double length,
            double crownRadius, double crownThickness) {
        // placing the output according to position
        IfcCartesianPoint location;
        IfcDirection xAxis;
        IfcDirection zAxis;
        if (position == 0) {
            // bottom output
            location = new IfcCartesianPoint(0, 0, 0);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, -1, 0);
        } else if (position == 1) {
            // right output
            location = new IfcCartesianPoint(0, length, outputs[0].length);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 0, -1);
        } else if (position == 2) {
            // top output
            location = new IfcCartesianPoint(0, 0, length + outputs[0].length);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 1, 0);
        } else {
            // left output, axes are the default ones
            location = new IfcCartesianPoint(0, -length, outputs[0].length);
            xAxis = null;
            zAxis = null;
        }
        IfcAxis2Placement3D outputPosition = new IfcAxis2Placement3D(location, zAxis, xAxis);
        IfcAxis1Placement rotationAxis = new IfcAxis1Placement(
                new IfcCartesianPoint(0, 0, 0),
                new IfcDirection(0, 1, 0));
        double innerRadius = radius - thickness;
        double innerLength = length * innerRadius / radius;
        IfcPolyline outputRightSection;

        if (crownRadius != 0 && crownThickness != 0) {
            // if flanged
            outputRightSection = new IfcPolyline(new IfcCartesianPoint(innerRadius, 0),
                    new IfcCartesianPoint(crownRadius, 0),
                    new IfcCartesianPoint(crownRadius, crownThickness),
                    new IfcCartesianPoint(radius * (length - crownThickness) / length, crownThickness),
                    new IfcCartesianPoint(0, length),
                    new IfcCartesianPoint(0, innerLength));
        } else {
            outputRightSection = new IfcPolyline(new IfcCartesianPoint(innerRadius, 0),
                    new IfcCartesianPoint(radius, 0),
                    new IfcCartesianPoint(0, length),
                    new IfcCartesianPoint(0, innerLength));
        }

        IfcArbitraryClosedProfileDef outputRightSectionWrapper = new IfcArbitraryClosedProfileDef(
                IfcProfileTypeEnum.AREA, null, outputRightSection);
        return new IfcRevolvedAreaSolid(outputRightSectionWrapper, outputPosition, rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));
    }

    /**
     * Adds a bottom output.
     * @param radius The radius of the output.
     * @param length The length of the output.
     * @param crownRadius The radius of the crown, must be 0 when the output
     * is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     * output is welded and not flanged.
     */
    ValveBuilder addBottomOutput(double radius, double length, double crownRadius, double crownThickness) {
        outputs[0] = new ValveOutput();
        outputs[0].radius = radius;
        outputs[0].length = length;
        outputs[0].crownRadius = crownRadius;
        outputs[0].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a right output.
     * @param radius The radius of the output.
     * @param length The length of the output.
     * @param crownRadius The radius of the crown, must be 0 when the output
     * is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     * output is welded and not flanged.
     */
    ValveBuilder addRightOutput(double radius, double length, double crownRadius, double crownThickness) {
        outputs[1] = new ValveOutput();
        outputs[1].radius = radius;
        outputs[1].length = length;
        outputs[1].crownRadius = crownRadius;
        outputs[1].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a top output.
     * @param radius The radius of the output.
     * @param length The length of the output.
     * @param crownRadius The radius of the crown, must be 0 when the output
     * is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     * output is welded and not flanged.
     */
    ValveBuilder addTopOutput(double radius, double length, double crownRadius, double crownThickness) {
        outputs[2] = new ValveOutput();
        outputs[2].radius = radius;
        outputs[2].length = length;
        outputs[2].crownRadius = crownRadius;
        outputs[2].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a left output.
     * @param radius The radius of the output.
     * @param length The length of the output.
     * @param crownRadius The radius of the crown, must be 0 when the output
     * is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     * output is welded and not flanged.
     */
    ValveBuilder addLeftOutput(double radius, double length, double crownRadius, double crownThickness) {
        outputs[3] = new ValveOutput();
        outputs[3].radius = radius;
        outputs[3].length = length;
        outputs[3].crownRadius = crownRadius;
        outputs[3].crownThickness = crownThickness;
        return this;
    }

    /**
     * Creates the Set of IfcRevolvedAreaSolid items that represent the valve.
     * @throws NullPointerException If {@link #addBottomOutput(double, double,
     * double, double)} was not called before this
     * method (because all kinds of valves have the
     * bottom output).
     */
    Set<IfcRepresentationItem> build() {
        if (outputs[0] == null) {
            throw new NullPointerException("a bottom output must be added before building the valve");
        }
        // we'll have at most 1 sphere and 4 outputs
        Set<IfcRepresentationItem> valveItems = new LinkedHashSet<>(6, 1);
        List<Double> possibleSphereRadiuses = new ArrayList<>(8);

        for (byte i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                valveItems.add(buildValveOutput(i,
                        outputs[i].radius,
                        outputs[i].length,
                        outputs[i].crownRadius,
                        outputs[i].crownThickness));
                possibleSphereRadiuses.add(outputs[i].radius);
                possibleSphereRadiuses.add(outputs[i].length);
            }
        }
        // creating the central sphere
        double sphereRadius = min(possibleSphereRadiuses);
        IfcAxis2Placement3D spherePosition = new IfcAxis2Placement3D(0, 0, outputs[0].length);
        IfcRevolvedAreaSolid sphere = EywaToIfcConverter.buildSphere(sphereRadius, spherePosition);
        valveItems.add(sphere);
        return valveItems;
    }

    /**
     * The parameters needed to build a valve output. If an output is not
     * flanged, {@code crownRadius} and {@code crownThickness} must be 0.
     */
    private static class ValveOutput {
        double radius;
        double length;
        double crownRadius;
        double crownThickness;
    }
}
