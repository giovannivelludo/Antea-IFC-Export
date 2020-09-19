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
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
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
     *
     * @throws NullPointerException     If values is null.
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
     *
     * @param position           0 indicates the bottom output, others follow
     *                           anticlockwise.
     * @param thickness          Thickness of the valve.
     * @param radius             Radius of the output.
     * @param length             Length of the output.
     * @param crownRadius        Radius of the crown if the valve is flanged, 0
     *                           otherwise.
     * @param crownThickness     Thickness of the crown if the valve is flanged,
     *                           0 otherwise.
     * @param bottomOutputLength Length of the bottom output, needed to place
     *                           other outputs correctly.
     * @return The Set containing the IfcBooleanResult geometries composing the
     * output.
     */
    private static Set<IfcRepresentationItem> buildValveOutput(byte position,
                                                               double thickness,
                                                               double radius,
                                                               double length,
                                                               double crownRadius,
                                                               double crownThickness,
                                                               double bottomOutputLength) {
        Set<IfcRepresentationItem> outputItems = new HashSet<>(2, 1);

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
            location = new IfcCartesianPoint(0, length, bottomOutputLength);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 0, -1);
        } else if (position == 2) {
            // top output
            location = new IfcCartesianPoint(0, 0, length + bottomOutputLength);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 1, 0);
        } else {
            // left output, all axes are the default ones
            location = new IfcCartesianPoint(0, -length, bottomOutputLength);
            xAxis = null;
            zAxis = null;
        }
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0));

        IfcPolyline outerTriangle = new IfcPolyline(new IfcCartesianPoint(0, 0),
                                                    new IfcCartesianPoint(radius,
                                                                          0),
                                                    new IfcCartesianPoint(0,
                                                                          length));
        IfcArbitraryClosedProfileDef outerTriangleWrapper =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 outerTriangle);
        IfcAxis2Placement3D conePosition =
                new IfcAxis2Placement3D(location, zAxis, xAxis);
        IfcRevolvedAreaSolid outerCone = new IfcRevolvedAreaSolid(
                outerTriangleWrapper,
                conePosition,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        double innerRadius = radius - thickness;
        IfcPolyline innerTriangle = new IfcPolyline(new IfcCartesianPoint(0, 0),
                                                    new IfcCartesianPoint(
                                                            innerRadius,
                                                            0),
                                                    new IfcCartesianPoint(0,
                                                                          (length /
                                                                                  radius) *
                                                                                  innerRadius));
        IfcArbitraryClosedProfileDef innerTriangleWrapper =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 innerTriangle);
        IfcRevolvedAreaSolid innerCone = new IfcRevolvedAreaSolid(
                innerTriangleWrapper,
                conePosition,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        IfcBooleanResult output =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     outerCone,
                                     innerCone);
        outputItems.add(output);

        // if flanged
        if (crownRadius != 0 && crownThickness != 0) {
            IfcRectangleProfileDef revolvedRectangle =
                    new IfcRectangleProfileDef(IfcProfileTypeEnum.AREA,
                                               null,
                                               new IfcAxis2Placement2D(
                                                       crownRadius / 2,
                                                       crownThickness / 2),
                                               new IfcPositiveLengthMeasure(
                                                       crownRadius),
                                               new IfcPositiveLengthMeasure(
                                                       crownThickness));
            IfcRevolvedAreaSolid crown = new IfcRevolvedAreaSolid(
                    revolvedRectangle,
                    conePosition,
                    rotationAxis,
                    new IfcPlaneAngleMeasure(2 * PI));
            IfcBooleanResult flange =
                    new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                         crown,
                                         outerCone);
            outputItems.add(flange);
        }
        return outputItems;
    }

    /**
     * Adds a bottom output.
     *
     * @param radius         The radius of the output.
     * @param length         The length of the output.
     * @param crownRadius    The radius of the crown, must be 0 when the output
     *                       is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     *                       output is welded and not flanged.
     */
    ValveBuilder addBottomOutput(double radius,
                                 double length,
                                 double crownRadius,
                                 double crownThickness) {
        outputs[0] = new ValveOutput();
        outputs[0].radius = radius;
        outputs[0].length = length;
        outputs[0].crownRadius = crownRadius;
        outputs[0].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a right output.
     *
     * @param radius         The radius of the output.
     * @param length         The length of the output.
     * @param crownRadius    The radius of the crown, must be 0 when the output
     *                       is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     *                       output is welded and not flanged.
     */
    ValveBuilder addRightOutput(double radius,
                                double length,
                                double crownRadius,
                                double crownThickness) {
        outputs[1] = new ValveOutput();
        outputs[1].radius = radius;
        outputs[1].length = length;
        outputs[1].crownRadius = crownRadius;
        outputs[1].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a top output.
     *
     * @param radius         The radius of the output.
     * @param length         The length of the output.
     * @param crownRadius    The radius of the crown, must be 0 when the output
     *                       is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     *                       output is welded and not flanged.
     */
    ValveBuilder addTopOutput(double radius,
                              double length,
                              double crownRadius,
                              double crownThickness) {
        outputs[2] = new ValveOutput();
        outputs[2].radius = radius;
        outputs[2].length = length;
        outputs[2].crownRadius = crownRadius;
        outputs[2].crownThickness = crownThickness;
        return this;
    }

    /**
     * Adds a left output.
     *
     * @param radius         The radius of the output.
     * @param length         The length of the output.
     * @param crownRadius    The radius of the crown, must be 0 when the output
     *                       is welded and not flanged.
     * @param crownThickness The thickness of the crown, must be 0 when the
     *                       output is welded and not flanged.
     */
    ValveBuilder addLeftOutput(double radius,
                               double length,
                               double crownRadius,
                               double crownThickness) {
        outputs[3] = new ValveOutput();
        outputs[3].radius = radius;
        outputs[3].length = length;
        outputs[3].crownRadius = crownRadius;
        outputs[3].crownThickness = crownThickness;
        return this;
    }

    /**
     * Creates the Set of IfcBooleanResult items that represent the valve.
     *
     * @throws NullPointerException If {@link #addBottomOutput(double, double,
     *                              double, double)} was not called (because all
     *                              kinds of valves have the bottom output).
     */
    Set<IfcRepresentationItem> build() {
        if (outputs[0] == null) {
            throw new NullPointerException(
                    "a bottom output must be added before building the valve");
        }
        // we'll have at most 1 sphere, 4 cones and 4 flanges
        Set<IfcRepresentationItem> valveItems = new HashSet<>(9, 1);
        List<Double> possibleSphereRadiuses = new ArrayList<>(8);

        for (byte i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                valveItems.addAll(buildValveOutput(i,
                                                   thickness,
                                                   outputs[i].radius,
                                                   outputs[i].length,
                                                   outputs[i].crownRadius,
                                                   outputs[i].crownThickness,
                                                   outputs[0].length));
                possibleSphereRadiuses.add(outputs[i].radius);
                possibleSphereRadiuses.add(outputs[i].length);
            }
        }
        // creating the central sphere
        IfcAxis2Placement2D circlePosition = new IfcAxis2Placement2D(0, 0);
        IfcPositiveLengthMeasure circleRadius =
                new IfcPositiveLengthMeasure(min(possibleSphereRadiuses));
        IfcCircleProfileDef circle =
                new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                                        null,
                                        circlePosition,
                                        circleRadius);
        IfcAxis2Placement3D spherePosition =
                new IfcAxis2Placement3D(0, 0, outputs[0].length);
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0));
        IfcRevolvedAreaSolid sphere = new IfcRevolvedAreaSolid(circle,
                                                               spherePosition,
                                                               rotationAxis,
                                                               new IfcPlaneAngleMeasure(
                                                                       PI));
        // all items in valveItems must be of type IfcBooleanResult, because
        // when they'll be put in an IfcShapeRepresentation their type will
        // have to be according to representationType "CSG"; so we have to
        // merge sphere with an object from valveItems
        IfcBooleanResult sphereWrapper =
                new IfcBooleanResult(IfcBooleanOperator.UNION,
                                     sphere,
                                     (IfcBooleanOperand) valveItems.iterator()
                                             .next());
        valveItems.add(sphereWrapper);
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
