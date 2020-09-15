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
import buildingsmart.io.Attribute;
import buildingsmart.io.Header;
import buildingsmart.io.InverseRelationship;
import buildingsmart.io.Serializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.imc.persistence.po.eytukan.*;
import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Math.*;

public class EywaToIfcConverter implements EywaConverter {

    private static final String COMPANY_NAME = "Antea";
    private static final String PROGRAM_NAME = "Antea IFC Export";
    private static final String PROGRAM_VERSION = "0.0.1-SNAPSHOT";
    private static final String PROGRAM_ID = "com.anteash:ifc";
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    /**
     * Representation context for all geometries.
     */
    private static final IfcGeometricRepresentationContext
            GEOMETRIC_REPRESENTATION_CONTEXT;

    static {
        IfcAxis2Placement3D worldCoordinateSystem =
                new IfcAxis2Placement3D(0, 0, 0);
        GEOMETRIC_REPRESENTATION_CONTEXT =
                new IfcGeometricRepresentationContext(new IfcLabel("Plan"),
                                                      new IfcLabel("Model"),
                                                      new IfcDimensionCount(3),
                                                      new IfcReal(1.E-08),
                                                      worldCoordinateSystem,
                                                      null);
    }

    /**
     * Owner history for all {@link IfcRoot} objects in this project.
     */
    private final IfcOwnerHistory ownerHistory;
    /**
     * The set containing the converted Eywa geometries.
     */
    private final Set<IfcProduct> geometries = new HashSet<>();
    /**
     * Maps each Primitive in the Eywa tree to its position relative to the
     * world coordinate system.
     */
    private final Map<Primitive, IfcAxis2Placement3D> objPositions =
            new HashMap<>();
    private Map<String, Object> hints;

    /**
     * Initializes {@code this.ownerHistory} because this field will be needed
     * when creating the IfcProducts that represent instances of Eywa
     * Primitives.
     */
    public EywaToIfcConverter() {
        IfcPerson person =
                IfcPerson.builder().givenName(new IfcLabel("")).build();
        IfcActorRole anteaRole =
                new IfcActorRole(IfcRoleEnum.CONSULTANT, null, null);
        IfcOrganization organization =
                IfcOrganization.builder().name(new IfcLabel(COMPANY_NAME))
                        .roles(Collections.singletonList(anteaRole)).build();
        IfcPersonAndOrganization personAndOrganization =
                new IfcPersonAndOrganization(person, organization, null);
        IfcApplication.clearUniqueConstraint();
        IfcApplication application = new IfcApplication(organization,
                                                        new IfcLabel(
                                                                PROGRAM_VERSION),
                                                        new IfcLabel(
                                                                PROGRAM_NAME),
                                                        new IfcIdentifier(
                                                                PROGRAM_ID));
        IfcTimeStamp currentTime = new IfcTimeStamp();
        this.ownerHistory = new IfcOwnerHistory(personAndOrganization,
                                                application,
                                                null,
                                                IfcChangeActionEnum.ADDED,
                                                currentTime,
                                                personAndOrganization,
                                                application,
                                                currentTime);
    }

    /**
     * Utility method to write the content of {@code project} to {@code
     * filePath}.
     *
     * @param project  The {@link IfcProject} to serialize.
     * @param filePath The path to the file to create, or to an already existing
     *                 file.
     * @throws IOException       If the file exists but is a directory rather
     *                           than a regular file, does not exist but cannot
     *                           be created, or cannot be opened for any other
     *                           reason; if an I/O error occurs during
     *                           serialization of {@code project}.
     * @throws SecurityException If a security manager exists and its
     *                           <code>{@link java.lang.SecurityManager#checkRead(java.lang.String)}</code>
     *                           method does not permit verification of the
     *                           existence of the file and directories named in
     *                           filePath, and all necessary parent directories;
     *                           or if the
     *                           <code>{@link java.lang.SecurityManager#checkWrite(java.lang.String)}</code>
     *                           method does not permit the named file,
     *                           directories and all necessary parent
     *                           directories to be created or written to.
     * @throws SecurityException If a required system property value cannot be
     *                           accessed while resolving the canonical path of
     *                           the file created in filePath.
     * @throws SecurityException Let {@code obj} be any node of the tree having
     *                           {@code project} as its root, where parent nodes
     *                           are Entity types and children are the {@link
     *                           Attribute}s and {@link InverseRelationship}s of
     *                           the parent node. This exception is thrown if a
     *                           security manager,
     *                           <i>s</i>, is present and any of the
     *                           following conditions is met:
     *                           <ul>
     *                             <li>
     *                               invocation of
     *                               {@link SecurityManager
     *                               #checkPermission(Permission)}
     *                               method with {@code
     *                               RuntimePermission
     *                               ("accessDeclaredMembers")} denies
     *                               access to the declared fields
     *                               within{@code obj.getClass()}
     *                             </li>
     *                             <li>
     *                               invocation of
     *                               {@link SecurityManager
     *                               #checkPackageAccess(String)} denies
     *                               access to the package of
     *                               {@code obj.getClass()}
     *                             </li>
     *                             <li>
     *                                access to private Fields of
     *                                {@code obj} by calling
     *                               {@link Field#setAccessible(boolean)}
     *                                is not permitted based on the
     *                                security policy currently in
     *                                effect.
     *                             </li>
     *                           </ul>
     */
    public static void writeToFile(IfcProject project, @NonNull String filePath)
            throws IOException {
        writeToFile(project, Serializer.createFile(filePath));
    }

    /**
     * Utility method to write the content of {@code project} to {@code
     * filePath}.
     *
     * @param project The {@link IfcProject} to serialize.
     * @param output  The file in which to serialize the project.
     * @throws NullPointerException If {@code output} is null.
     * @throws IOException          If the file exists but is a directory rather
     *                              than a regular file, does not exist but
     *                              cannot be created, or cannot be opened for
     *                              any other reason; if an I/O error occurs
     *                              during serialization of {@code project}.
     * @throws SecurityException    If a required system property value cannot
     *                              be accessed while resolving the canonical
     *                              path of {@code output}.
     * @throws SecurityException    Let {@code obj} be any node of the tree
     *                              having {@code project} as its root, where
     *                              parent nodes are Entity types and children
     *                              are the {@link Attribute}s and {@link
     *                              InverseRelationship}s of the parent node.
     *                              This exception is thrown if a security
     *                              manager,
     *                              <i>s</i>, is present and any of the
     *                              following conditions is met:
     *                              <ul>
     *                                <li>
     *                                  invocation of
     *                                  {@link SecurityManager
     *                                  #checkPermission(Permission)}
     *                                  method with {@code
     *                                  RuntimePermission
     *                                  ("accessDeclaredMembers")} denies
     *                                  access to the declared fields
     *                                  within{@code obj.getClass()}
     *                                </li>
     *                                <li>
     *                                  invocation of
     *                                  {@link SecurityManager
     *                                  #checkPackageAccess(String)} denies
     *                                  access to the package of
     *                                  {@code obj.getClass()}
     *                                </li>
     *                                <li>
     *                                   access to private Fields of
     *                                   {@code obj} by calling
     *                                  {@link Field#setAccessible(boolean)}
     *                                   is not permitted based on the
     *                                   security policy currently in
     *                                   effect.
     *                                </li>
     *                              </ul>
     */
    public static void writeToFile(IfcProject project, @NonNull File output)
            throws IOException {
        Header header =
                new Header().setDescription("ViewDefinition [CoordinationView]")
                        .setOrganization(COMPANY_NAME).setOriginatingSystem(
                        PROGRAM_NAME + " " + PROGRAM_VERSION);
        new Serializer().serialize(header, project, output);
    }

    /**
     * @param matrix An array representing a 4x4 matrix in column-major order.
     * @param vector A 4-dimensional vector.
     * @return A 4-dimensional vector that is result of the matrix
     * multiplication matrix * vector.
     *
     * @throws NullPointerException     If any of the arguments are {@code
     *                                  null}.
     * @throws IllegalArgumentException If the length of {@code matrix} is not
     *                                  16 or the length of {@code vector} is
     *                                  not 4.
     */
    private static double[] multiply(@NonNull Double[] matrix,
                                     @NonNull double[] vector) {
        if (matrix.length != 16) {
            throw new IllegalArgumentException("matrix must have 16 elements");
        }
        if (vector.length != 4) {
            throw new IllegalArgumentException("vector must have 4 elements");
        }
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) { // row index
            result[i] = 0;
            for (int j = 0; j < 4; j++) { // column index
                result[i] += matrix[i + j * 4] * vector[j];
            }
        }
        return result;
    }

    /**
     * @param obj The object for which to return the description.
     * @return The {@code description} field of {@code obj} in the JSON format.
     *
     * @throws NullPointerException If {@code obj} is {@code null}.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    private static String getDescription(@NonNull Primitive obj) {
        Map<String, Object> description = obj.getDescription();
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(description);
        } catch (JsonProcessingException e) {
            throw new ConversionException(e);
        }
    }

    /**
     * Useful for Eywa Primitives having a "switched" field. This method
     * modifies the relativePlacement of the corresponding IfcProduct so that it
     * will be upside-down. {@code unflipped} won't be modified.
     *
     * @param unflipped {@code IfcProduct.ObjectPlacement.RelativePlacement} of
     *                  the IfcProduct to flip.
     * @param length    The length of the object measured along its z axis
     *                  (which corresponds to the y axis in the Eywa
     *                  specification).
     * @return The placement of the flipped object.
     */
    private static IfcLocalPlacement flip(IfcLocalPlacement unflipped,
                                          double length) {
        IfcAxis2Placement3D localCoordSys =
                (IfcAxis2Placement3D) unflipped.getRelativePlacement();

        // shifting the origin upwards along the local z axis
        double[] originCoords =
                localCoordSys.getLocation().getCoordinates().stream()
                        .mapToDouble(IfcLengthMeasure::getValue).toArray();
        double[] zAxisNormCoords =
                localCoordSys.getAxis().getNormalisedDirectionRatios().stream()
                        .mapToDouble(IfcReal::getValue).toArray();
        double[] shiftedOriginCoords = IntStream.range(0, originCoords.length)
                .mapToDouble(i -> originCoords[i] + length * zAxisNormCoords[i])
                .toArray();
        IfcCartesianPoint shiftedOrigin =
                new IfcCartesianPoint(shiftedOriginCoords);

        // flipping the z axis
        double[] flippedAxisCoords =
                localCoordSys.getAxis().getDirectionRatios().stream()
                        .mapToDouble(ifcreal -> -ifcreal.getValue()).toArray();
        IfcDirection flippedAxis = new IfcDirection(flippedAxisCoords);

        // this doesn't modify the IfcAxis2Placement3D associated to obj
        // in objPositions, which must not be modified because it's used to
        // calculate the location of children of obj
        IfcAxis2Placement3D newLocalCoordSys = new IfcAxis2Placement3D(
                shiftedOrigin,
                flippedAxis,
                localCoordSys.getRefDirection());
        return new IfcLocalPlacement(unflipped.getPlacementRelTo(),
                                     newLocalCoordSys);
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
     * Given the parameters of 4 valve outputs, creates the Set of
     * IfcBooleanResult items that represent the valve.
     *
     * @param outputs      The 4 valve outputs, where {@code outputs[0]} is the
     *                     bottom one and others follow anticlockwise. If an
     *                     output is not present it must be null.
     * @param objThickness Thickness of the valve outputs.
     * @return The set of IfcBooleanResult geometries representing the valve.
     *
     * @throws NullPointerException     If outputs is null, if {@code
     *                                  outputs[0]} is null (because all kinds
     *                                  of valves have the bottom output).
     * @throws IllegalArgumentException If outputs doesn't have exactly 4
     *                                  elements.
     */
    private static Set<IfcRepresentationItem> buildValve(@NonNull ValveOutput[] outputs,
                                                         Double objThickness) {
        if (outputs[0] == null) {
            throw new NullPointerException("outputs[0] cannot be null");
        }
        if (outputs.length != 4) {
            throw new IllegalArgumentException(
                    "outputs must contain exactly 4 elements");
        }
        // default thickness is 0.1
        double thickness = objThickness == null || objThickness == 0d ||
                objThickness == -0d ? 0.1 : objThickness;
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
     * Builds a valve output according to the given parameters.
     *
     * @param position       0 indicates the bottom output, others follow
     *                       anticlockwise.
     * @param thickness      Thickness of the valve.
     * @param radius         Radius of the output.
     * @param length         Length of the output.
     * @param crownRadius    Radius of the crown if the valve is flanged, 0
     *                       otherwise.
     * @param crownThickness Thickness of the crown if the valve is flanged, 0
     *                       otherwise.
     * @param output0Length  Length of the bottom output, needed to place other
     *                       outputs correctly.
     * @return The Set containing the IfcBooleanResult geometries composing the
     * output.
     */
    private static Set<IfcRepresentationItem> buildValveOutput(byte position,
                                                               double thickness,
                                                               double radius,
                                                               double length,
                                                               double crownRadius,
                                                               double crownThickness,
                                                               double output0Length) {
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
            location = new IfcCartesianPoint(0, length, output0Length);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 0, -1);
        } else if (position == 2) {
            // top output
            location = new IfcCartesianPoint(0, 0, length + output0Length);
            xAxis = new IfcDirection(1, 0, 0);
            zAxis = new IfcDirection(0, 1, 0);
        } else {
            // left output, all axes are the default ones
            location = new IfcCartesianPoint(0, -length, output0Length);
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
            IfcPolyline crownSection =
                    new IfcPolyline(new IfcCartesianPoint(0, 0),
                                    new IfcCartesianPoint(crownRadius, 0),
                                    new IfcCartesianPoint(crownRadius,
                                                          crownThickness),
                                    new IfcCartesianPoint(0, crownThickness));
            IfcArbitraryClosedProfileDef crownSectionWrapper =
                    new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                     null,
                                                     crownSection);
            IfcRevolvedAreaSolid crown = new IfcRevolvedAreaSolid(
                    crownSectionWrapper,
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
     * @return The result of the conversion.
     */
    @Override
    public IfcProject getResult() {
        IfcSIUnit millimeter = new IfcSIUnit(IfcUnitEnum.LENGTHUNIT,
                                             IfcSIPrefix.MILLI,
                                             IfcSIUnitName.METRE);
        IfcSIUnit squaremillimeter = new IfcSIUnit(IfcUnitEnum.AREAUNIT,
                                                   IfcSIPrefix.MILLI,
                                                   IfcSIUnitName.SQUARE_METRE);
        IfcSIUnit cubicmillimeter = new IfcSIUnit(IfcUnitEnum.VOLUMEUNIT,
                                                  IfcSIPrefix.MILLI,
                                                  IfcSIUnitName.CUBIC_METRE);
        IfcSIUnit radian = new IfcSIUnit(IfcUnitEnum.PLANEANGLEUNIT,
                                         null,
                                         IfcSIUnitName.RADIAN);
        IfcUnitAssignment unitAssignment = new IfcUnitAssignment(millimeter,
                                                                 squaremillimeter,
                                                                 cubicmillimeter,
                                                                 radian);

        String projectName = hints == null ? null : (String) hints.get("name");
        IfcProject ifcProject =
                IfcProject.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory).name(new IfcLabel(
                        projectName == null ? "Unnamed" : projectName))
                        .representationContext(GEOMETRIC_REPRESENTATION_CONTEXT)
                        .unitsInContext(unitAssignment).build();

        IfcSite ifcSite = IfcSite.builder().globalId(new IfcGloballyUniqueId())
                .ownerHistory(ownerHistory)
                .compositionType(IfcElementCompositionEnum.COMPLEX).build();

        IfcRelAggregates.builder().globalId(new IfcGloballyUniqueId())
                .ownerHistory(ownerHistory)
                .name(new IfcLabel("Project to site link"))
                .relatingObject(ifcProject).relatedObject(ifcSite).build();

        IfcRelContainedInSpatialStructure.
                builder().globalId(new IfcGloballyUniqueId())
                .ownerHistory(ownerHistory)
                .name(new IfcLabel("Site to geometries link"))
                .relatingStructure(ifcSite).relatedElements(geometries).build();

        return ifcProject;
    }

    /**
     * Call this method to use EywaRoot's hints in the conversion (if
     * possible).
     *
     * @param hints {@link EywaRoot#getHints()}
     */
    @Override
    public void addHints(Map<String, Object> hints) {
        this.hints = hints;
    }

    /**
     * Resolves the location of {@code obj} and adds it to {@code
     * objPositions}.
     *
     * @param obj The object of which to resolve the absolute location.
     * @return The location of the object relative to the world coordinate
     * system.
     *
     * @throws NullPointerException     If obj is {@code null}.
     * @throws IllegalArgumentException If in {@code obj} the matrix field is
     *                                  not set and the rotation field is.
     */
    private IfcLocalPlacement resolveLocation(@NonNull Primitive obj) {
        IfcAxis2Placement3D objPosition;
        IfcAxis2Placement3D parentPosition = objPositions.get(obj.getParent());
        if (parentPosition == null) {
            // obj is the root object
            parentPosition = new IfcAxis2Placement3D(0, 0, 0);
        }

        if (obj.getMatrix() != null) {
            Double[] matrix = obj.getMatrix();
            double[] location = new double[4];
            double[] axis = new double[4];
            double[] refDir = new double[4];
            List<IfcLengthMeasure> locationCoordinates =
                    parentPosition.getLocation().getCoordinates();
            List<IfcReal> axisCoordinates =
                    parentPosition.getP().get(2).getDirectionRatios(); // z axis
            List<IfcReal> refDirectionCoordinates =
                    parentPosition.getP().get(0).getDirectionRatios(); // x axis
            for (int i = 0; i < locationCoordinates.size(); i++) {
                location[i] = locationCoordinates.get(i).getValue();
                axis[i] = axisCoordinates.get(i).getValue();
                refDir[i] = refDirectionCoordinates.get(i).getValue();
            }
            location[3] = 1; // indicates that location is a position
            axis[3] = 0;
            refDir[3] = 0; // axis and refDir are directions

            double[] newLocation = new double[3];
            double[] newAxis = new double[3];
            double[] newRefDirection = new double[3];
            System.arraycopy(multiply(matrix, location), 0, newLocation, 0, 3);
            System.arraycopy(multiply(matrix, axis), 0, newAxis, 0, 3);
            System.arraycopy(multiply(matrix, refDir),
                             0,
                             newRefDirection,
                             0,
                             3);
            objPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(newLocation),
                                            new IfcDirection(newAxis),
                                            new IfcDirection(newRefDirection));
        } else if (obj.getRotationArray() != null &&
                !(Arrays.equals(obj.getRotationArray(),
                                new Double[]{0d, 0d, 0d}) ||
                        Arrays.equals(obj.getRotationArray(),
                                      new Double[]{-0d, -0d, -0d}))) {
            throw new IllegalArgumentException(
                    "conversion of objects with both position and " +
                            "rotation set is currently not supported");
        } else {
            Double[] position = obj.getPosition();
            if (position == null) {
                position = new Double[]{0d, 0d, 0d};
            }

            double[] location =
                    parentPosition.getLocation().getCoordinates().stream()
                            .mapToDouble(IfcLengthMeasure::getValue).toArray();
            double[] newLocation = new double[]{location[0] + position[0],
                                                location[1] + position[1],
                                                location[2] + position[2]};

            objPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(newLocation),
                                            parentPosition.getAxis(),
                                            parentPosition.getRefDirection());
        }
        objPositions.put(obj, objPosition);
        return new IfcLocalPlacement(null, objPosition);
    }

    /**
     * @param obj The {@link Beam} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Beam obj) {

    }

    /**
     * @param obj The {@link Blind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Blind obj) {
        boolean hasPlate =
                obj.getCrownRadius() != null && obj.getCrownRadius() != 0;
        Set<IfcRepresentationItem> blindItems =
                new HashSet<>(hasPlate ? 2 : 1, 1);
        double blindRadius = hasPlate ? obj.getCrownRadius() : obj.getRadius();
        IfcDirection extrusionDirection = new IfcDirection(0, 0, 1);

        IfcAxis2Placement2D sectionPlacement = new IfcAxis2Placement2D(0, 0);
        IfcCircleProfileDef blindSection = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionPlacement,
                new IfcPositiveLengthMeasure(blindRadius));
        IfcAxis2Placement3D blindPlacement = new IfcAxis2Placement3D(0, 0, 0);
        IfcExtrudedAreaSolid blind = new IfcExtrudedAreaSolid(blindSection,
                                                              blindPlacement,
                                                              extrusionDirection,
                                                              new IfcLengthMeasure(
                                                                      obj.getCrownThickness()));
        blindItems.add(blind);

        if (hasPlate) {
            IfcCircleProfileDef plateSection = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    sectionPlacement,
                    new IfcPositiveLengthMeasure(obj.getRadius()));
            IfcAxis2Placement3D platePlacement =
                    new IfcAxis2Placement3D(0, 0, obj.getCrownThickness());
            IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(plateSection,
                                                                  platePlacement,
                                                                  extrusionDirection,
                                                                  new IfcLengthMeasure(
                                                                          obj.getCrownThickness() /
                                                                                  10));
            blindItems.add(plate);
        }

        IfcLocalPlacement location = resolveLocation(obj);
        if (obj.isSwitched()) {
            double length;
            if (obj.getCrownRadius() == null) {
                length = obj.getCrownThickness();
            } else {
                length = obj.getCrownThickness() + obj.getCrownThickness() / 10;
            }
            location = flip(location, length);
        }
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                blindItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy blindProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(blindProxy);
    }

    /**
     * @param obj The {@link Box} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Box obj) {

    }

    /**
     * @param obj The {@link Collar} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Collar obj) {

    }

    /**
     * @param obj The {@link Curve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Curve obj) {
        Double radius = obj.getRadius();
        if (radius == null) {
            if (!obj.getRadius1().equals(obj.getRadius2())) {
                new IllegalArgumentException(
                        "conversion of Curves with radius1 differing from " +
                                "radius2 is currently not supported")
                        .printStackTrace();
            }
            radius = obj.getRadius1();
        }

        IfcAxis2Placement2D profilePosition =
                new IfcAxis2Placement2D(0, obj.getCurveRadius());
        IfcCircleHollowProfileDef profile = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                profilePosition,
                new IfcPositiveLengthMeasure(radius),
                new IfcPositiveLengthMeasure(obj.getThickness()));
        IfcAxis2Placement3D curvePlacement = new IfcAxis2Placement3D(0, 0, 0);
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid curve = new IfcRevolvedAreaSolid(profile,
                                                              curvePlacement,
                                                              rotationAxis,
                                                              new IfcPlaneAngleMeasure(
                                                                      obj.getAngle()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                curve);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy curveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(curveProxy);
    }

    /**
     * @param obj The {@link Dielectric} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Dielectric obj) {

    }

    /**
     * @param obj The {@link Dish} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Dish obj) {

    }

    /**
     * @param obj The {@link DualExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull DualExpansionJoint obj) {

    }

    /**
     * @param obj The {@link EccentricCone} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull EccentricCone obj) {
        resolveLocation(obj);
        // FIXME: use an IfcManifoldSolidBrep, this means the shape
        //  of the eccentric cone will be approximated using polygons.
        new IllegalArgumentException(
                "conversion of EccentricCones is currently not supported")
                .printStackTrace();
    }

    /**
     * @param obj The {@link Empty} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Empty obj) {
        resolveLocation(obj);
    }

    /**
     * @param obj The {@link Endplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Endplate obj) {
        IfcGeometricRepresentationItem endplate;
        IfcExtrudedAreaSolid neck = null;
        boolean hasNeck = obj.getNeck() != null && obj.getNeck() != 0;

        if (hasNeck) {
            IfcAxis2Placement2D neckSectionPosition =
                    new IfcAxis2Placement2D(0, 0);
            IfcCircleHollowProfileDef neckSection =
                    new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                                  null,
                                                  neckSectionPosition,
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getRadius()),
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getThickness()));
            IfcAxis2Placement3D neckPosition = new IfcAxis2Placement3D(0, 0, 0);
            neck = new IfcExtrudedAreaSolid(neckSection,
                                            neckPosition,
                                            new IfcDirection(0, 0, 1),
                                            new IfcLengthMeasure(obj.getNeck()));
        }

        double semiAxis2 = obj.getDish() != null ? obj.getDish() :
                obj.getCambering() * obj.getRadius();
        if (semiAxis2 == 0) {
            IfcAxis2Placement2D plateSectionPosition =
                    new IfcAxis2Placement2D(0, 0);
            IfcCircleProfileDef plateSection = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    plateSectionPosition,
                    new IfcPositiveLengthMeasure(obj.getRadius()));
            IfcAxis2Placement3D platePosition =
                    new IfcAxis2Placement3D(0, 0, obj.getNeck());
            IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(plateSection,
                                                                  platePosition,
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
                                                                  //FIXME: use
                                                                  // endThickness
                                                                  new IfcLengthMeasure(
                                                                          obj.getThickness()));
            if (hasNeck) {
                endplate = new IfcBooleanResult(IfcBooleanOperator.UNION,
                                                neck,
                                                plate);
            } else {
                endplate = plate;
            }
        } else {
            IfcAxis2Placement2D ellipsePosition = new IfcAxis2Placement2D(0, 0);
            IfcEllipseProfileDef outerEllipse = new IfcEllipseProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    ellipsePosition,
                    new IfcPositiveLengthMeasure(obj.getRadius()),
                    new IfcPositiveLengthMeasure(semiAxis2));
            IfcAxis2Placement3D outerEllipsoidPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  obj.getNeck()),
                                            // the z axis is rotated by PI/2
                                            // towards the negative y axis, and
                                            // the y axis becomes the
                                            // vertical axis
                                            new IfcDirection(0, -1, 0),
                                            new IfcDirection(1, 0, 0));
            IfcRevolvedAreaSolid outerEllipsoid = new IfcRevolvedAreaSolid(
                    outerEllipse,
                    outerEllipsoidPosition,
                    new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                          new IfcDirection(0, 1, 0)),
                    new IfcPlaneAngleMeasure(PI));

            IfcEllipseProfileDef innerEllipse = new IfcEllipseProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    ellipsePosition,
                    new IfcPositiveLengthMeasure(
                            obj.getRadius() - obj.getThickness()),
                    new IfcPositiveLengthMeasure(
                            semiAxis2 - obj.getThickness()));
            IfcAxis2Placement3D innerEllipsoidPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  obj.getNeck()),
                                            // the z axis is rotated by PI/2
                                            // towards the negative y axis, and
                                            // the y axis becomes the
                                            // vertical axis
                                            new IfcDirection(0, -1, 0),
                                            new IfcDirection(1, 0, 0));
            IfcRevolvedAreaSolid innerEllipsoid = new IfcRevolvedAreaSolid(
                    innerEllipse,
                    innerEllipsoidPosition,
                    new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                          new IfcDirection(0, 1, 0)),
                    new IfcPlaneAngleMeasure(PI));

            IfcBooleanResult ellipsoidDifference = new IfcBooleanResult(
                    IfcBooleanOperator.DIFFERENCE,
                    outerEllipsoid,
                    innerEllipsoid);

            IfcPlane cuttingPlane =
                    new IfcPlane(new IfcAxis2Placement3D(0, 0, obj.getNeck()));
            IfcHalfSpaceSolid halfSpace =
                    new IfcHalfSpaceSolid(cuttingPlane, true);
            IfcBooleanClippingResult halfEllipsoid =
                    new IfcBooleanClippingResult(IfcBooleanOperator.DIFFERENCE,
                                                 ellipsoidDifference,
                                                 halfSpace);
            if (hasNeck) {
                endplate = new IfcBooleanResult(IfcBooleanOperator.UNION,
                                                neck,
                                                halfEllipsoid);
            } else {
                endplate = halfEllipsoid;
            }
        }

        IfcLocalPlacement objectPlacement = resolveLocation(obj);
        if (obj.isSwitched()) {
            double length = 0;
            if (hasNeck) {
                length = obj.getNeck();
            }
            if (semiAxis2 != 0) {
                length += semiAxis2;
            } else {
                length += obj.getThickness(); //FIXME: use endThickness
            }
            objectPlacement = flip(objectPlacement, length);
        }

        String representationType =
                endplate instanceof IfcExtrudedAreaSolid ? "SweptSolid" : "CSG";
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel(representationType),
                endplate);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy endplateProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(objectPlacement)
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(endplateProxy);
    }

    /**
     * @param obj The {@link ExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull ExpansionJoint obj) {

    }

    /**
     * @param obj The {@link FaceSet} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull FaceSet obj) {

    }

    /**
     * @param obj The {@link FourWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull FourWaysValve obj) {
        ValveOutput[] outputs = new ValveOutput[4];
        outputs[0] = new ValveOutput();
        outputs[1] = new ValveOutput();
        outputs[2] = new ValveOutput();
        outputs[3] = new ValveOutput();
        outputs[0].radius = obj.getRadius1();
        outputs[0].length = obj.getLength1();
        outputs[0].crownRadius = obj.getCrownRadius1();
        outputs[0].crownThickness = obj.getCrownThickness1();
        outputs[1].radius = obj.getRadius2();
        outputs[1].length = obj.getLength2();
        outputs[1].crownRadius = obj.getCrownRadius2();
        outputs[1].crownThickness = obj.getCrownThickness2();
        outputs[2].radius = obj.getRadius3();
        outputs[2].length = obj.getLength3();
        outputs[2].crownRadius = obj.getCrownRadius3();
        outputs[2].crownThickness = obj.getCrownThickness3();
        outputs[3].radius = obj.getRadius4();
        outputs[3].length = obj.getLength4();
        outputs[3].crownRadius = obj.getCrownRadius4();
        outputs[3].crownThickness = obj.getCrownThickness4();
        Set<IfcRepresentationItem> valveItems =
                buildValve(outputs, obj.getThickness());

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy valveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(valveProxy);
    }

    /**
     * @param obj The {@link Instrument} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Instrument obj) {

    }

    /**
     * @param obj The {@link Ladder} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Ladder obj) {

    }

    /**
     * @param obj The {@link Mesh} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Mesh obj) {

    }

    /**
     * @param obj The {@link Nozzle} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Nozzle obj) {
        Set<IfcRepresentationItem> nozzleItems = new HashSet<>(4, 1);
        boolean hasTrunk =
                obj.getTrunkLength() != null && obj.getTrunkLength() != 0;
        boolean hasTang =
                obj.getTangLength() != null && obj.getTangLength() != 0;
        double raisedFaceLength = obj.getCrownThickness() / 10;
        double voidRadius = obj.getRadius() - obj.getThickness();
        double raisedFaceRadius =
                obj.getRadius() + (obj.getCrownRadius() - obj.getRadius()) / 3;
        IfcAxis2Placement2D sectionPosition = new IfcAxis2Placement2D(0, 0);
        IfcDirection extrusionDirection = new IfcDirection(0, 0, 1);

        if (hasTrunk) {
            IfcCircleHollowProfileDef trunkSection =
                    new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                                  null,
                                                  sectionPosition,
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getRadius()),
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getThickness()));
            IfcAxis2Placement3D trunkPosition =
                    new IfcAxis2Placement3D(0, 0, 0);
            IfcExtrudedAreaSolid trunk = new IfcExtrudedAreaSolid(trunkSection,
                                                                  trunkPosition,
                                                                  extrusionDirection,
                                                                  new IfcLengthMeasure(
                                                                          obj.getTrunkLength()));
            nozzleItems.add(trunk);
        }

        if (hasTang) {
            IfcPolyline trapezium =
                    new IfcPolyline(new IfcCartesianPoint(voidRadius, 0),
                                    new IfcCartesianPoint(
                                            voidRadius + obj.getThickness(), 0),
                                    new IfcCartesianPoint(raisedFaceRadius,
                                                          obj.getTangLength()),
                                    new IfcCartesianPoint(voidRadius,
                                                          obj.getTangLength()));
            IfcArbitraryClosedProfileDef sweptArea =
                    new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                     null,
                                                     trapezium);
            IfcAxis2Placement3D tangPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  hasTrunk ?
                                                                          obj.getTrunkLength() :
                                                                          0),
                                            // the z axis is rotated by PI/2
                                            // towards the negative y axis, and
                                            // the y axis becomes the
                                            // vertical axis
                                            new IfcDirection(0, -1, 0),
                                            new IfcDirection(1, 0, 0));
            IfcRevolvedAreaSolid tang = new IfcRevolvedAreaSolid(sweptArea,
                                                                 tangPosition,
                                                                 new IfcAxis1Placement(
                                                                         new IfcCartesianPoint(
                                                                                 0,
                                                                                 0,
                                                                                 0),
                                                                         new IfcDirection(
                                                                                 0,
                                                                                 1,
                                                                                 0)),
                                                                 new IfcPlaneAngleMeasure(
                                                                         2 *
                                                                                 PI));
            nozzleItems.add(tang);
        }

        IfcCircleHollowProfileDef crownSection = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionPosition,
                new IfcPositiveLengthMeasure(obj.getCrownRadius()),
                new IfcPositiveLengthMeasure(
                        obj.getCrownRadius() - voidRadius));
        double crownZOffset = 0;
        if (hasTrunk) {
            crownZOffset = obj.getTrunkLength();
        }
        if (hasTang) {
            crownZOffset += obj.getTangLength();
        }
        IfcAxis2Placement3D crownPosition =
                new IfcAxis2Placement3D(0, 0, crownZOffset);
        IfcExtrudedAreaSolid crown = new IfcExtrudedAreaSolid(crownSection,
                                                              crownPosition,
                                                              extrusionDirection,
                                                              new IfcLengthMeasure(
                                                                      obj.getCrownThickness()));
        nozzleItems.add(crown);

        IfcCircleHollowProfileDef raisedFaceSection =
                new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                              null,
                                              sectionPosition,
                                              new IfcPositiveLengthMeasure(
                                                      raisedFaceRadius),
                                              new IfcPositiveLengthMeasure(
                                                      raisedFaceRadius -
                                                              voidRadius));
        double raisedFaceZOffset = obj.getCrownThickness();
        if (hasTrunk) {
            raisedFaceZOffset += obj.getTrunkLength();
        }
        if (hasTang) {
            raisedFaceZOffset += obj.getTangLength();
        }
        IfcAxis2Placement3D raisedFacePosition =
                new IfcAxis2Placement3D(0, 0, raisedFaceZOffset);
        IfcExtrudedAreaSolid raisedFace = new IfcExtrudedAreaSolid(
                raisedFaceSection,
                raisedFacePosition,
                extrusionDirection,
                new IfcLengthMeasure(raisedFaceLength));
        nozzleItems.add(raisedFace);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                nozzleItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);

        IfcLocalPlacement objectPlacement = resolveLocation(obj);
        if (obj.isSwitched()) {
            double length = 0;
            if (hasTrunk) {
                length = obj.getTrunkLength();
            }
            if (hasTang) {
                length += obj.getTangLength();
            }
            length += obj.getCrownThickness() + raisedFaceLength;
            objectPlacement = flip(objectPlacement, length);
        }
        IfcProxy nozzleProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(objectPlacement)
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(nozzleProxy);
    }

    /**
     * @param obj The {@link OrthoValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull OrthoValve obj) {
        ValveOutput[] outputs = new ValveOutput[4];
        outputs[0] = new ValveOutput();
        outputs[1] = new ValveOutput();
        outputs[0].radius = obj.getRadius1();
        outputs[0].length = obj.getLength1();
        outputs[1].radius = obj.getRadius2();
        outputs[1].length = obj.getLength2();
        if (obj.getWelded() != null && obj.getWelded()) {
            outputs[0].crownRadius = obj.getCrownRadius1();
            outputs[0].crownThickness = obj.getCrownThickness1();
            outputs[1].crownRadius = obj.getCrownRadius2();
            outputs[1].crownThickness = obj.getCrownThickness2();
        } else {
            outputs[0].crownRadius = 0;
            outputs[0].crownThickness = 0;
            outputs[1].crownRadius = 0;
            outputs[1].crownThickness = 0;
        }

        Set<IfcRepresentationItem> valveItems =
                buildValve(outputs, obj.getThickness());

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy valveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(valveProxy);
    }

    /**
     * @param obj The {@link RectangularBlind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull RectangularBlind obj) {
        IfcAxis2Placement3D origin = new IfcAxis2Placement3D(0, 0, 0);
        double halfWidth = obj.getWidth() / 2;
        double halfDepth = obj.getDepth() / 2;
        IfcPolyline blindSection =
                new IfcPolyline(new IfcCartesianPoint(halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, -halfDepth),
                                new IfcCartesianPoint(halfWidth, -halfDepth));
        IfcArbitraryClosedProfileDef blindSectionWrapper =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 blindSection);
        IfcExtrudedAreaSolid blind = new IfcExtrudedAreaSolid(
                blindSectionWrapper,
                origin,
                new IfcDirection(0, 0, 1),
                new IfcLengthMeasure(obj.getThickness()));

        double plateHalfWidth = halfWidth - obj.getCrownWidth();
        double plateHalfDepth = halfDepth - obj.getCrownDepth();
        IfcPolyline plateSection = new IfcPolyline(new IfcCartesianPoint(
                plateHalfWidth,
                plateHalfDepth),
                                                   new IfcCartesianPoint(-plateHalfWidth,
                                                                         plateHalfDepth),
                                                   new IfcCartesianPoint(-plateHalfWidth,
                                                                         -plateHalfDepth),
                                                   new IfcCartesianPoint(
                                                           plateHalfWidth,
                                                           -plateHalfDepth));
        IfcArbitraryClosedProfileDef plateSectionWrapper =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 plateSection);
        IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(
                plateSectionWrapper,
                origin,
                new IfcDirection(0, 0, -1),
                new IfcLengthMeasure(obj.getThickness() / 10));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                blind,
                plate);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcLocalPlacement location = resolveLocation(obj);
        if (obj.isSwitched()) {
            location = flip(location,
                            obj.getThickness() + obj.getThickness() / 10);
        }
        IfcProxy rectBlind =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(rectBlind);
    }

    /**
     * @param obj The {@link RectangularEndplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull RectangularEndplate obj) {
        IfcDirection extrusionDirection = new IfcDirection(0, 0, 1);
        double neckLength = obj.getLength() - obj.getEndThickness();
        Set<IfcRepresentationItem> endplateItems =
                new HashSet<>(neckLength == 0 ? 1 : 2, 1);

        double halfWidth = obj.getWidth() / 2;
        double halfDepth = obj.getDepth() / 2;
        IfcPolyline outerRect =
                new IfcPolyline(new IfcCartesianPoint(halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, -halfDepth),
                                new IfcCartesianPoint(halfWidth, -halfDepth));
        IfcArbitraryClosedProfileDef plateSection =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 outerRect);
        IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(plateSection,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      neckLength),
                                                              extrusionDirection,
                                                              new IfcLengthMeasure(
                                                                      obj.getEndThickness()));
        endplateItems.add(plate);

        if (neckLength != 0) {
            double innerHalfWidth = halfWidth - obj.getThickness();
            double innerHalfDepth = halfDepth - obj.getThickness();
            IfcPolyline innerRect = new IfcPolyline(new IfcCartesianPoint(
                    innerHalfWidth,
                    innerHalfDepth),
                                                    new IfcCartesianPoint(-innerHalfWidth,
                                                                          innerHalfDepth),
                                                    new IfcCartesianPoint(-innerHalfWidth,
                                                                          -innerHalfDepth),
                                                    new IfcCartesianPoint(
                                                            innerHalfWidth,
                                                            -innerHalfDepth));
            IfcArbitraryProfileDefWithVoids section =
                    new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                        null,
                                                        outerRect,
                                                        innerRect);
            IfcExtrudedAreaSolid neck = new IfcExtrudedAreaSolid(section,
                                                                 new IfcAxis2Placement3D(
                                                                         0,
                                                                         0,
                                                                         0),
                                                                 extrusionDirection,
                                                                 new IfcLengthMeasure(
                                                                         neckLength));
            endplateItems.add(neck);
        }

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                endplateItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcLocalPlacement location = resolveLocation(obj);
        if (obj.isSwitched()) {
            location = flip(location, obj.getLength());
        }
        IfcProxy rectEndplate =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(rectEndplate);
    }

    /**
     * @param obj The {@link RectangularFlange} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull RectangularFlange obj) {
        IfcDirection extrusionDirection = new IfcDirection(0, 0, 1);

        double halfNeckWidth = obj.getWidth() / 2;
        double halfNeckDepth = obj.getDepth() / 2;
        IfcPolyline outerNeckSection = new IfcPolyline(new IfcCartesianPoint(
                halfNeckWidth,
                halfNeckDepth),
                                                       new IfcCartesianPoint(-halfNeckWidth,
                                                                             halfNeckDepth),
                                                       new IfcCartesianPoint(-halfNeckWidth,
                                                                             -halfNeckDepth),
                                                       new IfcCartesianPoint(
                                                               halfNeckWidth,
                                                               -halfNeckDepth));
        double halfInnerNeckWidth = halfNeckWidth - obj.getThickness();
        double halfInnerNeckDepth = halfNeckDepth - obj.getThickness();
        IfcPolyline innerNeckSection = new IfcPolyline(new IfcCartesianPoint(
                halfInnerNeckWidth,
                halfInnerNeckDepth),
                                                       new IfcCartesianPoint(-halfInnerNeckWidth,
                                                                             halfInnerNeckDepth),
                                                       new IfcCartesianPoint(-halfInnerNeckWidth,
                                                                             -halfInnerNeckDepth),
                                                       new IfcCartesianPoint(
                                                               halfInnerNeckWidth,
                                                               -halfInnerNeckDepth));
        IfcArbitraryProfileDefWithVoids neckSection =
                new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                    null,
                                                    outerNeckSection,
                                                    innerNeckSection);
        IfcExtrudedAreaSolid neck = new IfcExtrudedAreaSolid(neckSection,
                                                             new IfcAxis2Placement3D(
                                                                     0,
                                                                     0,
                                                                     0),
                                                             extrusionDirection,
                                                             new IfcLengthMeasure(
                                                                     obj.getNeck()));


        double halfCrownWidth = halfInnerNeckWidth + obj.getCrownWidth();
        double halfCrownDepth = halfInnerNeckDepth + obj.getCrownDepth();
        IfcPolyline outerCrownSection = new IfcPolyline(new IfcCartesianPoint(
                halfCrownWidth,
                halfCrownDepth),
                                                        new IfcCartesianPoint(-halfCrownWidth,
                                                                              halfCrownDepth),
                                                        new IfcCartesianPoint(-halfCrownWidth,
                                                                              -halfCrownDepth),
                                                        new IfcCartesianPoint(
                                                                halfCrownWidth,
                                                                -halfCrownDepth));
        IfcArbitraryProfileDefWithVoids crownSection =
                new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                    null,
                                                    outerCrownSection,
                                                    innerNeckSection);
        IfcExtrudedAreaSolid crown = new IfcExtrudedAreaSolid(crownSection,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      obj.getNeck()),
                                                              extrusionDirection,
                                                              new IfcLengthMeasure(
                                                                      obj.getCrownThickness()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                neck,
                crown);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy rectFlange =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(rectFlange);
    }

    /**
     * @param obj The {@link RectangularPlate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull RectangularPlate obj) {
        double halfWidth = obj.getWidth() / 2;
        double halfDepth = obj.getDepth() / 2;
        IfcPolyline outerRect =
                new IfcPolyline(new IfcCartesianPoint(halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, -halfDepth),
                                new IfcCartesianPoint(halfWidth, -halfDepth));
        double innerHalfWidth = obj.getHoleWidth() / 2;
        double innerHalfDepth = obj.getHoleDepth() / 2;
        IfcPolyline innerRect = new IfcPolyline(new IfcCartesianPoint(
                innerHalfWidth,
                innerHalfDepth),
                                                new IfcCartesianPoint(-innerHalfWidth,
                                                                      innerHalfDepth),
                                                new IfcCartesianPoint(-innerHalfWidth,
                                                                      -innerHalfDepth),
                                                new IfcCartesianPoint(
                                                        innerHalfWidth,
                                                        -innerHalfDepth));
        IfcArbitraryProfileDefWithVoids section =
                new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                    null,
                                                    outerRect,
                                                    innerRect);
        IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(section,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      0),
                                                              new IfcDirection(0,
                                                                               0,
                                                                               1),
                                                              new IfcLengthMeasure(
                                                                      obj.getThickness()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                plate);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy rectPlate =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(rectPlate);
    }

    /**
     * @param obj The {@link RectangularShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull RectangularShell obj) {
        double halfWidth = obj.getWidth() / 2;
        double halfDepth = obj.getDepth() / 2;
        IfcPolyline outerRect =
                new IfcPolyline(new IfcCartesianPoint(halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, halfDepth),
                                new IfcCartesianPoint(-halfWidth, -halfDepth),
                                new IfcCartesianPoint(halfWidth, -halfDepth));
        double innerHalfWidth = halfWidth - obj.getThickness();
        double innerHalfDepth = halfDepth - obj.getThickness();
        IfcPolyline innerRect = new IfcPolyline(new IfcCartesianPoint(
                innerHalfWidth,
                innerHalfDepth),
                                                new IfcCartesianPoint(-innerHalfWidth,
                                                                      innerHalfDepth),
                                                new IfcCartesianPoint(-innerHalfWidth,
                                                                      -innerHalfDepth),
                                                new IfcCartesianPoint(
                                                        innerHalfWidth,
                                                        -innerHalfDepth));
        IfcArbitraryProfileDefWithVoids section =
                new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                    null,
                                                    outerRect,
                                                    innerRect);
        IfcExtrudedAreaSolid rectShell = new IfcExtrudedAreaSolid(section,
                                                                  new IfcAxis2Placement3D(
                                                                          0,
                                                                          0,
                                                                          0),
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
                                                                  new IfcLengthMeasure(
                                                                          obj.getLength()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                rectShell);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy rectShellProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(rectShellProxy);
    }

    /**
     * @param obj The {@link Ring} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Ring obj) {
        IfcCircleHollowProfileDef section = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                new IfcAxis2Placement2D(0, 0),
                new IfcPositiveLengthMeasure(obj.getOuterRadius()),
                new IfcPositiveLengthMeasure(
                        obj.getOuterRadius() - obj.getInnerRadius()));
        IfcExtrudedAreaSolid ring = new IfcExtrudedAreaSolid(section,
                                                             new IfcAxis2Placement3D(
                                                                     0,
                                                                     0,
                                                                     0),
                                                             new IfcDirection(0,
                                                                              0,
                                                                              1),
                                                             new IfcLengthMeasure(
                                                                     obj.getThickness()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                ring);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy ringProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(ringProxy);
    }

    /**
     * @param obj The {@link Shell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Shell obj) {
        //creating a parallelogram that is the right part of the vertical
        // section of the shell
        IfcPolyline trapezium = new IfcPolyline(new IfcCartesianPoint(
                obj.getRadius1() - obj.getThickness(), 0),
                                                new IfcCartesianPoint(obj.getRadius1(),
                                                                      0),
                                                new IfcCartesianPoint(obj.getRadius2(),
                                                                      obj.getLength()),
                                                new IfcCartesianPoint(
                                                        obj.getRadius2() -
                                                                obj.getThickness(),
                                                        obj.getLength()));
        IfcArbitraryClosedProfileDef sweptArea =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 trapezium);
        IfcAxis2Placement3D shellPosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        // the z axis is rotated by PI/2
                                        // towards the negative y axis, and
                                        // the y axis becomes the vertical axis
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid shell = new IfcRevolvedAreaSolid(sweptArea,
                                                              shellPosition,
                                                              new IfcAxis1Placement(
                                                                      new IfcCartesianPoint(
                                                                              0,
                                                                              0,
                                                                              0),
                                                                      new IfcDirection(
                                                                              0,
                                                                              1,
                                                                              0)),
                                                              new IfcPlaneAngleMeasure(
                                                                      2 * PI));
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                shell);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy shellProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(shellProxy);
    }

    /**
     * @param obj The {@link Sphere} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Sphere obj) {

    }

    /**
     * @param obj The {@link Stair} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Stair obj) {

    }

    /**
     * @param obj The {@link Sweep} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Sweep obj) {

    }

    /**
     * @param obj The {@link TankShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull TankShell obj) {

    }

    /**
     * @param obj The {@link Tee} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Tee obj) {
        IfcAxis2Placement2D sectionsPosition = new IfcAxis2Placement2D(0, 0);
        IfcAxis2Placement3D mainPipePosition = new IfcAxis2Placement3D(0, 0, 0);
        IfcDirection positiveExtrusionDirection = new IfcDirection(0, 0, 1);
        IfcDirection negativeExtrusionDirection = new IfcDirection(0, 0, -1);

        IfcCircleProfileDef outerPipe1Section = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(obj.getRadius1()));
        IfcExtrudedAreaSolid outerPipe1 = new IfcExtrudedAreaSolid(
                outerPipe1Section,
                mainPipePosition,
                negativeExtrusionDirection,
                new IfcLengthMeasure(obj.getLength1()));
        IfcCircleProfileDef innerPipe1Section = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(
                        obj.getRadius1() - obj.getThickness()));
        IfcExtrudedAreaSolid innerPipe1 = new IfcExtrudedAreaSolid(
                innerPipe1Section,
                mainPipePosition,
                negativeExtrusionDirection,
                new IfcLengthMeasure(obj.getLength1()));

        IfcCircleProfileDef outerPipe2Section = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(obj.getRadius2()));
        IfcExtrudedAreaSolid outerPipe2 = new IfcExtrudedAreaSolid(
                outerPipe2Section,
                mainPipePosition,
                positiveExtrusionDirection,
                new IfcLengthMeasure(obj.getLength2()));
        IfcCircleProfileDef innerPipe2Section = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(
                        obj.getRadius2() - obj.getThickness()));
        IfcExtrudedAreaSolid innerPipe2 = new IfcExtrudedAreaSolid(
                innerPipe2Section,
                mainPipePosition,
                positiveExtrusionDirection,
                new IfcLengthMeasure(obj.getLength2()));

        IfcBooleanResult outerPipe =
                new IfcBooleanResult(IfcBooleanOperator.UNION,
                                     outerPipe1,
                                     outerPipe2);
        IfcBooleanResult innerPipe =
                new IfcBooleanResult(IfcBooleanOperator.UNION,
                                     innerPipe1,
                                     innerPipe2);
        IfcBooleanResult pipe =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     outerPipe,
                                     innerPipe);


        double derivationThickness =
                obj.getDerivationThickness() == null ? obj.getThickness() :
                        obj.getDerivationThickness();
        IfcAxis2Placement3D derivPipePosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0,
                                                         cos(obj.getPhi()),
                                                         sin(obj.getPhi())),
                                        new IfcDirection(1, 0, 0));
        IfcCircleProfileDef outerDerivPipeSection = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(obj.getDerivationRadius()));
        IfcExtrudedAreaSolid outerDerivPipe = new IfcExtrudedAreaSolid(
                outerDerivPipeSection,
                derivPipePosition,
                positiveExtrusionDirection,
                new IfcLengthMeasure(obj.getDerivationLength()));
        IfcCircleProfileDef innerDerivPipeSection = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sectionsPosition,
                new IfcPositiveLengthMeasure(
                        obj.getDerivationRadius() - derivationThickness));
        IfcExtrudedAreaSolid innerDerivPipe = new IfcExtrudedAreaSolid(
                innerDerivPipeSection,
                derivPipePosition,
                positiveExtrusionDirection,
                new IfcLengthMeasure(obj.getDerivationLength()));

        IfcBooleanResult derivationPipe = new IfcBooleanResult(
                IfcBooleanOperator.DIFFERENCE,
                outerDerivPipe,
                innerDerivPipe);


        derivationPipe = new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                              derivationPipe,
                                              outerPipe);
        pipe = new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                    pipe,
                                    innerDerivPipe);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                pipe,
                derivationPipe);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy teeProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(teeProxy);
    }

    /**
     * @param obj The {@link ThreeWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull ThreeWaysValve obj) {
        ValveOutput[] outputs = new ValveOutput[4];
        outputs[0] = new ValveOutput();
        outputs[1] = new ValveOutput();
        outputs[2] = new ValveOutput();
        outputs[0].radius = obj.getRadius1();
        outputs[0].length = obj.getLength1();
        outputs[0].crownRadius = obj.getCrownRadius1();
        outputs[0].crownThickness = obj.getCrownThickness1();
        outputs[1].radius = obj.getRadius2();
        outputs[1].length = obj.getLength2();
        outputs[1].crownRadius = obj.getCrownRadius2();
        outputs[1].crownThickness = obj.getCrownThickness2();
        outputs[2].radius = obj.getRadius3();
        outputs[2].length = obj.getLength3();
        outputs[2].crownRadius = obj.getCrownRadius3();
        outputs[2].crownThickness = obj.getCrownThickness3();
        Set<IfcRepresentationItem> valveItems =
                buildValve(outputs, obj.getThickness());

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy valveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(valveProxy);
    }

    /**
     * @param obj The {@link Valve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during the serialization
     *                              of {@link Primitive#getDescription()} in the
     *                              JSON format.
     */
    @Override
    public void addObject(@NonNull Valve obj) {
        ValveOutput[] outputs = new ValveOutput[4];
        outputs[0] = new ValveOutput();
        outputs[2] = new ValveOutput();
        outputs[0].radius = obj.getRadius1();
        outputs[0].length = obj.getLength1();
        outputs[2].radius = obj.getRadius2();
        outputs[2].length = obj.getLength2();
        if (obj.getFlanged() != null && obj.getFlanged()) {
            outputs[0].crownRadius = obj.getCrownRadius1();
            outputs[0].crownThickness = obj.getCrownThickness1();
            outputs[2].crownRadius = obj.getCrownRadius2();
            outputs[2].crownThickness = obj.getCrownThickness2();
        } else {
            outputs[0].crownRadius = 0;
            outputs[0].crownThickness = 0;
            outputs[2].crownRadius = 0;
            outputs[2].crownThickness = 0;
        }
        Set<IfcRepresentationItem> valveItems =
                buildValve(outputs, obj.getThickness());

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy valveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(valveProxy);
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
