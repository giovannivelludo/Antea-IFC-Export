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

    /**
     * If {@code true}, the position of the converted objects will be relative
     * to the world coordinate system, otherwise it will be relative to the
     * position of the IfcProduct representing their parent in the Eywa tree.
     */
    private static final boolean USE_ABSOLUTE_PLACEMENTS = false;
    /**
     * Number of points used to draw circles in
     * {@link #addObject(EccentricCone)}.
     */
    private static final int RADIAL_SEGMENTS = 32;

    private static final String COMPANY_NAME = "Antea";
    private static final String PROGRAM_NAME = "Antea IFC Export";
    private static final String PROGRAM_VERSION = "0.0.1-SNAPSHOT";
    private static final String PROGRAM_ID = "com.anteash:ifc";
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    /**
     * Representation context for all geometries.
     */
    private static final IfcGeometricRepresentationContext
            GEOMETRIC_REPRESENTATION_CONTEXT =
            new IfcGeometricRepresentationContext(new IfcLabel("Plan"),
                                                  new IfcLabel("Model"),
                                                  new IfcDimensionCount(3),
                                                  new IfcReal(1.E-08),
                                                  new IfcAxis2Placement3D(0,
                                                                          0,
                                                                          0),
                                                  null);
    /**
     * The set containing the converted Eywa geometries.
     */
    private final Set<IfcProduct> geometries = new HashSet<>();
    /**
     * Maps each Primitive in the Eywa tree to its placement.
     */
    private final Map<Primitive, IfcLocalPlacement> objPositions =
            new HashMap<>();
    /**
     * Owner history for all {@link IfcRoot} objects in this project.
     */
    private IfcOwnerHistory ownerHistory;
    private Map<String, Object> hints;

    /**
     * Creates a new instance of this class, which can be reused for multiple
     * conversions after calling {@link #getResult()}.
     */
    public EywaToIfcConverter() {
        initializeOwnerHistory();
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
     * Useful for Eywa Primitives having a "switched" field. This method creates
     * a new IfcLocalPlacement for the corresponding IfcProduct so that it will
     * be upside-down. {@code unflipped} won't be modified.
     *
     * @param unflipped {@code IfcProduct.ObjectPlacement} of the IfcProduct to
     *                  flip.
     * @param length    The length of the object measured along its z axis
     *                  (which corresponds to the y axis in the Eywa coordinate
     *                  system).
     * @return The placement of the flipped object.
     *
     * @throws NullPointerException If unflipped is null.
     */
    private static IfcLocalPlacement flip(@NonNull IfcLocalPlacement unflipped,
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
     * Rotates the given vector.
     *
     * @param vector    The vector to rotate. It must use the Eywa coordinate
     *                  system and have the x, y, z and w components.
     * @param rotations Euler angles in radians describing the 3 rotations.
     * @param order     Indicates on what axis each rotation must be applied,
     *                  for example {@code "XYZ"} indicates that the first
     *                  rotation should be done around the X axis, the second
     *                  one on the Y axis, and the third one on the Z axis.
     * @throws NullPointerException     If any of the arguments are null.
     * @throws IllegalArgumentException If {@code vector}'s length is not 4, if
     *                                  {@code rotations}'s or {@code order}'s
     *                                  length is not 3, if order contains any
     *                                  characters other than 'X', 'Y' and 'Z'.
     */
    private static void rotate(@NonNull double[] vector,
                               @NonNull Double[] rotations,
                               @NonNull String order) {
        if (vector.length != 4) {
            throw new IllegalArgumentException("length of vector must be 4");
        } else if (rotations.length != 3) {
            throw new IllegalArgumentException("length of rotations must be 3");
        }
        if (!order.matches("^[XYZ]{3}$")) {
            throw new IllegalArgumentException(
                    "order must be 3 characters long and can contain only the" +
                            " characters 'X', 'Y' and 'Z'");
        }
        double[] oldVector = new double[vector.length];
        for (byte i = 0; i < order.length(); i++) {
            double ang = rotations[i];
            System.arraycopy(vector, 0, oldVector, 0, vector.length);
            switch (order.charAt(i)) {
                case 'X':
                    vector[1] =
                            oldVector[1] * cos(ang) - oldVector[2] * sin(ang);
                    vector[2] =
                            oldVector[1] * sin(ang) + oldVector[2] * cos(ang);
                    break;
                case 'Y':
                    vector[0] =
                            oldVector[0] * cos(ang) + oldVector[2] * sin(ang);
                    vector[2] =
                            -oldVector[0] * sin(ang) + oldVector[2] * cos(ang);
                    break;
                default: // Z
                    vector[0] =
                            oldVector[0] * cos(ang) - oldVector[1] * sin(ang);
                    vector[1] =
                            oldVector[0] * sin(ang) + oldVector[1] * cos(ang);
                    break;
            }
        }
    }

    /**
     * @param p The {@link Primitive} for which to return the thickness.
     * @return The Primitive's thickness if it exist and is bigger than 0, 0.1
     * otherwise.
     */
    private static double getSafeThickness(Primitive p) {
        return p == null || p.getThickness() == null || p.getThickness() <= 0 ?
                0.1 : p.getThickness();
    }

    /**
     * @param angles  The array containing the angles (in radians) at which
     *                points composing the two circles should be drawn.
     * @param radius1 The radius of the bottom circle.
     * @param radius2 The radius of the top circle.
     * @param length  The length of the eccentric cone.
     * @return An IfcFacetedBrep representing an eccentric cone (without any
     * internal voids).
     *
     * @throws NullPointerException     If {@code angles} is {@code null}.
     * @throws IllegalArgumentException If {@code angles} contains less than 3
     *                                  elements.
     */
    private static IfcFacetedBrep buildEccentricCone(@NonNull double[] angles,
                                                     double radius1,
                                                     double radius2,
                                                     double length) {
        if (angles.length < 3) {
            throw new IllegalArgumentException(
                    "angles must contain at least 3 elements");
        }
        IfcCartesianPoint[] outerBottomCircle = Arrays.stream(angles)
                .mapToObj(angle -> new IfcCartesianPoint(cos(angle) * radius1,
                                                         sin(angle) * radius1,
                                                         0))
                .toArray(IfcCartesianPoint[]::new);
        double radiusDifference = radius1 - radius2;
        IfcCartesianPoint[] outerTopCircle = Arrays.stream(angles)
                .mapToObj(angle -> new IfcCartesianPoint(cos(angle) * radius2,
                                                         sin(angle) * radius2 +
                                                                 radiusDifference,
                                                         length))
                .toArray(IfcCartesianPoint[]::new);

        Set<IfcFace> faces = new HashSet<>(3 + angles.length, 1);
        IfcPolyLoop bottomBase = new IfcPolyLoop(outerBottomCircle);
        IfcPolyLoop topBase = new IfcPolyLoop(outerTopCircle);
        faces.add(new IfcFace(new IfcFaceBound(bottomBase, IfcBoolean.T)));
        faces.add(new IfcFace(new IfcFaceBound(topBase, IfcBoolean.T)));

        // adding side faces of the cone
        IntStream.range(0, outerBottomCircle.length - 1)
                .mapToObj(i -> new IfcPolyLoop(outerBottomCircle[i],
                                               outerBottomCircle[i + 1],
                                               outerTopCircle[i + 1],
                                               outerTopCircle[i]))
                .map(polygon -> new IfcFace(new IfcFaceBound(polygon,
                                                             IfcBoolean.T)))
                .forEach(faces::add);
        // last face
        int lastPoint = outerBottomCircle.length - 1;
        IfcPolyLoop lastFace = new IfcPolyLoop(outerBottomCircle[lastPoint],
                                               outerTopCircle[lastPoint],
                                               outerTopCircle[0],
                                               outerBottomCircle[0]);
        faces.add(new IfcFace(new IfcFaceBound(lastFace, IfcBoolean.T)));
        return new IfcFacetedBrep(new IfcClosedShell(faces));
    }

    /**
     * Method used to convert both {@link ExpansionJoint} and {@link
     * DualExpansionJoint}, since they represent the same geometry.
     *
     * @param radius    Radius of the ExpansionJoint.
     * @param length    Length of the ExpansionJoint.
     * @param thickness Thickness of the ExpansionJoint.
     * @return The {@link IfcProductDefinitionShape} containing the geometries
     * that represent the ExpansionJoint, ready to be put in the {@link
     * IfcProduct} that is the conversion of the ExpansionJoint.
     */
    private static IfcProductDefinitionShape buildExpansionJoint(double radius,
                                                                 double length,
                                                                 double thickness) {
        double radiusThird = radius / 3;
        double radiusPlusRadiusThird = radius + radiusThird;
        double radiusMinusRadiusThird = radius - radiusThird;
        double lengthThirtieth = length / 30;
        double innerRadius = radius - thickness;
        double innerRadiusPlusRadiusThird = radius + radiusThird;
        double innerRadiusMinusRadiusThird = radius - radiusThird;

        // creating the right part of the vertical section of the expansionJoint
        IfcPolyline expJointSection =
                new IfcPolyline(new IfcCartesianPoint(radius, 0),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      lengthThirtieth),
                                new IfcCartesianPoint(radiusMinusRadiusThird,
                                                      3 * lengthThirtieth),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      5 * lengthThirtieth),
                                new IfcCartesianPoint(radiusMinusRadiusThird,
                                                      7 * lengthThirtieth),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      9 * lengthThirtieth),
                                new IfcCartesianPoint(radius,
                                                      10 * lengthThirtieth),
                                new IfcCartesianPoint(radius,
                                                      20 * lengthThirtieth),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      21 * lengthThirtieth),
                                new IfcCartesianPoint(radiusMinusRadiusThird,
                                                      23 * lengthThirtieth),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      25 * lengthThirtieth),
                                new IfcCartesianPoint(radiusMinusRadiusThird,
                                                      27 * lengthThirtieth),
                                new IfcCartesianPoint(radiusPlusRadiusThird,
                                                      29 * lengthThirtieth),
                                new IfcCartesianPoint(radius, length),
                                new IfcCartesianPoint(innerRadius, length),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      29 * lengthThirtieth),
                                new IfcCartesianPoint(
                                        innerRadiusMinusRadiusThird,
                                        27 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      25 * lengthThirtieth),
                                new IfcCartesianPoint(
                                        innerRadiusMinusRadiusThird,
                                        23 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      21 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadius,
                                                      20 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadius,
                                                      10 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      9 * lengthThirtieth),
                                new IfcCartesianPoint(
                                        innerRadiusMinusRadiusThird,
                                        7 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      5 * lengthThirtieth),
                                new IfcCartesianPoint(
                                        innerRadiusMinusRadiusThird,
                                        3 * lengthThirtieth),
                                new IfcCartesianPoint(innerRadiusPlusRadiusThird,
                                                      lengthThirtieth),
                                new IfcCartesianPoint(innerRadius, 0));
        IfcArbitraryClosedProfileDef expJointSectionWrapper =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 expJointSection);
        IfcAxis2Placement3D expJointPosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        // the z axis is rotated by PI/2
                                        // towards the negative y axis, and
                                        // the y axis becomes the vertical axis
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid expansionJoint = new IfcRevolvedAreaSolid(
                expJointSectionWrapper,
                expJointPosition,
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0)),
                new IfcPlaneAngleMeasure(2 * PI));
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                expansionJoint);
        return new IfcProductDefinitionShape(null, null, shapeRepresentation);
    }

    /**
     * Translates a vector in the IFC coordinate system to the equivalent vector
     * in the Eywa coordinate system, as axes x, y and z in IFC correspond to
     * axes z, x and y in Eywa.
     *
     * @param vector     The vector in the IFC coordinate system.
     * @param isPosition {@code true} if the vector is a position in space,
     *                   {@code false} if it's a direction.
     * @return An array of 4 elements containing the components of the vector in
     * the Eywa coordinate system.
     *
     * @throws NullPointerException     If {@code vector} is {@code null}.
     * @throws IllegalArgumentException If length of {@code vector} is not 3.
     */
    private static double[] ifcToEywaVector(@NonNull double[] vector,
                                            boolean isPosition) {
        if (vector.length != 3) {
            throw new IllegalArgumentException(
                    "length of vector must be 3, as it needs to have the x, y" +
                            " and z components");
        }
        double[] result = new double[4];
        result[0] = vector[1];
        result[1] = vector[2];
        result[2] = vector[0];
        result[3] = isPosition ? 1 : 0;
        return result;
    }

    /**
     * Translates a vector in the Eywa coordinate system to the equivalent
     * vector in the IFC coordinate system, as axes x, y and z in Eywa
     * correspond to axes y, z and x in IFC.
     *
     * @param vector The vector in the Eywa coordinate system.
     * @return An array of 3 elements containing the components of the vector in
     * the IFC coordinate system.
     *
     * @throws NullPointerException     If {@code vector} is {@code null}.
     * @throws IllegalArgumentException If length of {@code vector} is not 4.
     */
    private static double[] eywaToIfcVector(@NonNull double[] vector) {
        if (vector.length != 4) {
            throw new IllegalArgumentException(
                    "length of vector must be 4 as it needs to have the x, y," +
                            " z and w components");
        }
        double[] result = new double[3];
        result[0] = vector[2];
        result[1] = vector[0];
        result[2] = vector[1];
        return result;
    }

    /**
     * Initializes {@code this.ownerHistory}. This field will be needed when
     * creating the IfcProducts that represent instances of Eywa Primitives.
     */
    private void initializeOwnerHistory() {
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
     * After calling this method internal state is reset, so that the instance
     * of this class can be used for another conversion.
     *
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

        // resetting fields used during the conversion
        geometries.clear();
        objPositions.clear();
        hints = null;

        // updating ownerHistory with a recent date, in case this object will
        // be used for another conversion
        initializeOwnerHistory();

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
        IfcAxis2Placement3D parentPosition;
        if (USE_ABSOLUTE_PLACEMENTS) {
            IfcLocalPlacement parentPlacement =
                    objPositions.get(obj.getParent());
            if (parentPlacement == null) {
                // obj is the root object
                parentPosition = new IfcAxis2Placement3D(0, 0, 0);
            } else {
                parentPosition = (IfcAxis2Placement3D) parentPlacement
                        .getRelativePlacement();
            }
        } else {
            parentPosition = new IfcAxis2Placement3D(0, 0, 0);
        }

        IfcAxis2Placement3D objPosition;
        if (obj.getMatrix() != null) {
            Double[] matrix = obj.getMatrix();

            double[] eywaParentLocation =
                    ifcToEywaVector(parentPosition.getLocation()
                                            .getCoordinates().stream()
                                            .mapToDouble(IfcLengthMeasure::getValue)
                                            .toArray(), true);
            double[] eywaParentAxis =
                    ifcToEywaVector(parentPosition.getP().get(2)
                                            .getDirectionRatios().stream()
                                            .mapToDouble(IfcReal::getValue)
                                            .toArray(), false);
            double[] eywaParentRefDir =
                    ifcToEywaVector(parentPosition.getP().get(0)
                                            .getDirectionRatios().stream()
                                            .mapToDouble(IfcReal::getValue)
                                            .toArray(), false);

            double[] eywaLocation = multiply(matrix, eywaParentLocation);
            double[] eywaAxis = multiply(matrix, eywaParentAxis);
            double[] eywaRefDir = multiply(matrix, eywaParentRefDir);

            double[] location = eywaToIfcVector(eywaLocation);
            double[] axis = eywaToIfcVector(eywaAxis);
            double[] refDir = eywaToIfcVector(eywaRefDir);

            objPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(location),
                                            new IfcDirection(axis),
                                            new IfcDirection(refDir));
        } else if (obj.getPosition() == null && obj.getRotation() == null) {
            objPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
        } else {
            // using position and rotation
            double[] eywaParentLocation =
                    ifcToEywaVector(parentPosition.getLocation()
                                            .getCoordinates().stream()
                                            .mapToDouble(IfcLengthMeasure::getValue)
                                            .toArray(), true);
            double[] eywaParentAxis =
                    ifcToEywaVector(parentPosition.getP().get(2)
                                            .getDirectionRatios().stream()
                                            .mapToDouble(IfcReal::getValue)
                                            .toArray(), false);
            double[] eywaParentRefDir =
                    ifcToEywaVector(parentPosition.getP().get(0)
                                            .getDirectionRatios().stream()
                                            .mapToDouble(IfcReal::getValue)
                                            .toArray(), false);

            Double[] position =
                    obj.getPosition() == null ? new Double[]{0d, 0d, 0d} :
                            obj.getPosition();
            eywaParentLocation[0] = eywaParentLocation[0] + position[0];
            eywaParentLocation[1] = eywaParentLocation[1] + position[1];
            eywaParentLocation[2] = eywaParentLocation[2] + position[2];

            Double[] rotationAngles =
                    obj.getRotationArray() == null ? new Double[]{0d, 0d, 0d} :
                            obj.getRotationArray();
            String rotationOrder = obj.getRotationAxis() == null ||
                    obj.getRotationAxis().equals("") ? "XYZ" :
                    obj.getRotationAxis();

            rotate(eywaParentAxis, rotationAngles, rotationOrder);
            rotate(eywaParentRefDir, rotationAngles, rotationOrder);

            double[] location = eywaToIfcVector(eywaParentLocation);
            double[] axis = eywaToIfcVector(eywaParentAxis);
            double[] refDir = eywaToIfcVector(eywaParentRefDir);

            objPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(location),
                                            new IfcDirection(axis),
                                            new IfcDirection(refDir));
        }
        IfcLocalPlacement objPlacement = USE_ABSOLUTE_PLACEMENTS ?
                new IfcLocalPlacement(null, objPosition) :
                new IfcLocalPlacement(objPositions.get(obj.getParent()),
                                      objPosition);
        objPositions.put(obj, objPlacement);
        return objPlacement;
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
        IfcProfileDef beamSection;
        switch (obj.getKind()) {
            case IPE:
            case INP:
            case HEA:
            case HEB:
                beamSection = IfcIShapeProfileDef.builder()
                        .profileType(IfcProfileTypeEnum.AREA)
                        .position(new IfcAxis2Placement2D(0, 0))
                        .overallWidth(new IfcPositiveLengthMeasure(obj.getWidth()))
                        .overallDepth(new IfcPositiveLengthMeasure(obj.getDepth()))
                        .webThickness(new IfcPositiveLengthMeasure(obj.getCoreThickness()))
                        .flangeThickness(new IfcPositiveLengthMeasure(obj.getSideThickness()))
                        .build();
                break;
            case UNP:
                beamSection = IfcUShapeProfileDef.builder()
                        .profileType(IfcProfileTypeEnum.AREA)
                        .position(new IfcAxis2Placement2D(0, 0))
                        .depth(new IfcPositiveLengthMeasure(obj.getDepth()))
                        .flangeWidth(new IfcPositiveLengthMeasure(obj.getWidth()))
                        .webThickness(new IfcPositiveLengthMeasure(obj.getCoreThickness()))
                        .flangeThickness(new IfcPositiveLengthMeasure(obj.getSideThickness()))
                        .build();
                break;
            case T:
                beamSection = IfcTShapeProfileDef.builder()
                        .profileType(IfcProfileTypeEnum.AREA)
                        .position(new IfcAxis2Placement2D(0, 0))
                        .depth(new IfcPositiveLengthMeasure(obj.getDepth()))
                        .flangeWidth(new IfcPositiveLengthMeasure(obj.getWidth()))
                        .webThickness(new IfcPositiveLengthMeasure(obj.getCoreThickness()))
                        .flangeThickness(new IfcPositiveLengthMeasure(obj.getSideThickness()))
                        .build();
                break;
            case L:
                if (obj.getCoreThickness().equals(obj.getSideThickness())) {
                    IfcLShapeProfileDef.IfcLShapeProfileDefBuilder builder =
                            IfcLShapeProfileDef.builder()
                                    .profileType(IfcProfileTypeEnum.AREA)
                                    .position(new IfcAxis2Placement2D(0, 0))
                                    .depth(new IfcPositiveLengthMeasure(obj.getDepth()))
                                    .thickness(new IfcPositiveLengthMeasure(obj.getCoreThickness()));
                    if (!obj.getDepth().equals(obj.getWidth())) {
                        builder.width(new IfcPositiveLengthMeasure(obj.getWidth()));
                    }
                    beamSection = builder.build();
                } else {
                    double halfWidth = obj.getWidth() / 2;
                    double halfDepth = obj.getDepth() / 2;
                    double halfInternalWidth =
                            halfWidth - obj.getCoreThickness();
                    double halfInternalDepth =
                            halfDepth - obj.getSideThickness();
                    IfcPolyline polyline =
                            new IfcPolyline(new IfcCartesianPoint(-halfWidth,
                                                                  halfDepth),
                                            new IfcCartesianPoint(-halfWidth,
                                                                  -halfDepth),
                                            new IfcCartesianPoint(halfWidth,
                                                                  -halfDepth),
                                            new IfcCartesianPoint(halfWidth,
                                                                  -halfInternalDepth),
                                            new IfcCartesianPoint(-halfInternalWidth,
                                                                  -halfInternalDepth),
                                            new IfcCartesianPoint(-halfInternalWidth,
                                                                  halfDepth));
                    beamSection = new IfcArbitraryClosedProfileDef(
                            IfcProfileTypeEnum.AREA,
                            null,
                            polyline);
                }
                break;
            case CONCRETE:
            case RECTANGULAR:
                beamSection =
                        new IfcRectangleProfileDef(IfcProfileTypeEnum.AREA,
                                                   null,
                                                   new IfcAxis2Placement2D(0,
                                                                           0),
                                                   new IfcPositiveLengthMeasure(
                                                           obj.getWidth()),
                                                   new IfcPositiveLengthMeasure(
                                                           obj.getDepth()));
                break;
            case PIPE:
                beamSection = new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                                                      null,
                                                      new IfcAxis2Placement2D(0,
                                                                              0),
                                                      new IfcPositiveLengthMeasure(
                                                              obj.getRadius()));
                break;
            default:
                beamSection = null;
        }

        IfcExtrudedAreaSolid beam = new IfcExtrudedAreaSolid(beamSection,
                                                             new IfcAxis2Placement3D(
                                                                     0,
                                                                     0,
                                                                     0),
                                                             new IfcDirection(0,
                                                                              0,
                                                                              1),
                                                             new IfcLengthMeasure(
                                                                     obj.getLength() ==
                                                                             null ?
                                                                             obj.getDepth() :
                                                                             obj.getLength()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                beam);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcMember beamProduct =
                IfcMember.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectType(new IfcLabel("member"))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(beamProduct);
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
                new HashSet<>(hasPlate ? 3 : 2, 1);
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
        IfcDistributionFlowElement blindProduct =
                IfcDistributionFlowElement.builder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape).build();
        geometries.add(blindProduct);
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
        IfcRectangleProfileDef boxSection = new IfcRectangleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                new IfcAxis2Placement2D(0, 0),
                new IfcPositiveLengthMeasure(obj.getWidth()),
                new IfcPositiveLengthMeasure(obj.getDepth()));
        IfcExtrudedAreaSolid box = new IfcExtrudedAreaSolid(boxSection,
                                                            new IfcAxis2Placement3D(
                                                                    0,
                                                                    0,
                                                                    0),
                                                            new IfcDirection(0,
                                                                             0,
                                                                             1),
                                                            new IfcLengthMeasure(
                                                                    obj.getLength()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                box);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcBuildingElementProxy boxProxy = IfcBuildingElementProxy.builder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(boxProxy);
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
        IfcCircleHollowProfileDef collarSection = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                new IfcAxis2Placement2D(0, 0),
                new IfcPositiveLengthMeasure(obj.getRadius()),
                new IfcPositiveLengthMeasure(getSafeThickness(obj)));
        IfcExtrudedAreaSolid collar = new IfcExtrudedAreaSolid(collarSection,
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
                collar);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcMember collarProduct =
                IfcMember.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectType(new IfcLabel("collar"))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(collarProduct);
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
                new IfcPositiveLengthMeasure(getSafeThickness(obj)));
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
        IfcFlowFitting curveProduct = IfcFlowFitting.flowFittingBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(curveProduct);
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
        double thickness = obj.getRadius() / 10;
        IfcCircleHollowProfileDef dielectricSection =
                new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                              null,
                                              new IfcAxis2Placement2D(0, 0),
                                              new IfcPositiveLengthMeasure(
                                                      obj.getRadius() +
                                                              thickness),
                                              new IfcPositiveLengthMeasure(
                                                      thickness));
        IfcExtrudedAreaSolid dielectric = new IfcExtrudedAreaSolid(
                dielectricSection,
                new IfcAxis2Placement3D(0, 0, -obj.getLength()),
                new IfcDirection(0, 0, 1),
                new IfcLengthMeasure(obj.getLength() * 3));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                dielectric);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowSegment dielectricProduct = IfcFlowSegment.flowSegmentBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(dielectricProduct);
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
        IfcCircleProfileDef circle =
                new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                                        null,
                                        new IfcAxis2Placement2D(0, 0),
                                        new IfcPositiveLengthMeasure(obj.getRadius()));
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0));
        IfcRevolvedAreaSolid sphere = new IfcRevolvedAreaSolid(circle,
                                                               new IfcAxis2Placement3D(
                                                                       0,
                                                                       0,
                                                                       0),
                                                               rotationAxis,
                                                               new IfcPlaneAngleMeasure(
                                                                       PI));

        IfcCartesianPoint cuttingPlaneLocation =
                new IfcCartesianPoint(obj.getDirection()[2] * obj.getDistance(),
                                      obj.getDirection()[0] * obj.getDistance(),
                                      obj.getDirection()[1] *
                                              obj.getDistance());
        IfcDirection planeNormal = new IfcDirection(obj.getDirection()[2],
                                                    obj.getDirection()[0],
                                                    obj.getDirection()[1]);
        IfcPlane cuttingPlane = new IfcPlane(new IfcAxis2Placement3D(
                cuttingPlaneLocation,
                planeNormal,
                new IfcDirection(1, 0, 0)));
        IfcHalfSpaceSolid cuttingPlaneWrapper =
                new IfcHalfSpaceSolid(cuttingPlane, IfcBoolean.F);
        IfcBooleanClippingResult dish = new IfcBooleanClippingResult(
                IfcBooleanOperator.DIFFERENCE,
                sphere,
                cuttingPlaneWrapper);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("Clipping"),
                dish);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy dishProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(dishProxy);
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
        IfcProductDefinitionShape expansionJoint =
                buildExpansionJoint(obj.getRadius(),
                                    obj.getLength(),
                                    getSafeThickness(obj));
        IfcFlowSegment expansionJointProduct =
                IfcFlowSegment.flowSegmentBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(expansionJoint).build();
        geometries.add(expansionJointProduct);
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
        double slice = 2 * PI / RADIAL_SEGMENTS;
        // angles at which the points composing the circle will be drawn
        double[] angles =
                IntStream.range(0, RADIAL_SEGMENTS).mapToDouble(i -> i * slice)
                        .toArray();

        IfcFacetedBrep outerCone = buildEccentricCone(angles,
                                                      obj.getRadius1(),
                                                      obj.getRadius2(),
                                                      obj.getLength());
        double innerRadius1 = obj.getRadius1() - getSafeThickness(obj);
        double innerRadius2 = obj.getRadius2() - getSafeThickness(obj);
        IfcFacetedBrep innerCone = buildEccentricCone(angles,
                                                      innerRadius1,
                                                      innerRadius2,
                                                      obj.getLength());
        IfcBooleanResult eccentricCone =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     outerCone,
                                     innerCone);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                eccentricCone);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowSegment eccentricConeProduct =
                IfcFlowSegment.flowSegmentBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(eccentricConeProduct);
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
                                                          getSafeThickness(obj)));
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
            double endThickness = obj.getEndThickness() == null ||
                    obj.getEndThickness() <= 0 ? getSafeThickness(obj) :
                    obj.getEndThickness();
            IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(plateSection,
                                                                  platePosition,
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
                                                                  new IfcLengthMeasure(
                                                                          endThickness));
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
                            obj.getRadius() - getSafeThickness(obj)),
                    new IfcPositiveLengthMeasure(
                            semiAxis2 - getSafeThickness(obj)));
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
                    new IfcHalfSpaceSolid(cuttingPlane, IfcBoolean.T);
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
                length += obj.getEndThickness();
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
        IfcDistributionFlowElement endplateProduct =
                IfcDistributionFlowElement.builder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(objectPlacement)
                        .representation(productDefinitionShape).build();
        geometries.add(endplateProduct);
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
        IfcProductDefinitionShape expansionJoint =
                buildExpansionJoint(obj.getRadius(),
                                    obj.getLength(),
                                    getSafeThickness(obj));
        IfcFlowSegment expansionJointProxy = IfcFlowSegment.flowSegmentBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(expansionJoint).build();
        geometries.add(expansionJointProxy);
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
        IfcCartesianPoint[] vertices =
                new IfcCartesianPoint[obj.getVertices().length / 3];
        IntStream.range(0, vertices.length).forEach(i -> {
            int offset = i * 3;
            vertices[i] = new IfcCartesianPoint(obj.getVertices()[offset + 2],
                                                obj.getVertices()[offset],
                                                obj.getVertices()[offset + 1]);
        });

        Set<IfcFace> faces = new HashSet<>();

        int vertSizeIndex = 0;
        while (vertSizeIndex < obj.getFaces().length) {
            int vertSize = obj.getFaces()[vertSizeIndex];
            IfcCartesianPoint[] polyVerts = new IfcCartesianPoint[vertSize];

            for (int i = 0, j = vertSizeIndex + 1; i < vertSize; i++, j++) {
                // i iterates on elements of polyVerts,
                // j iterates on the indices of vertices composing the polygon
                polyVerts[i] = vertices[obj.getFaces()[j]];
            }

            try {
                IfcPolyLoop polygon = new IfcPolyLoop(polyVerts);
                faces.add(new IfcFace(new IfcFaceBound(polygon, IfcBoolean.T)));
            } catch (IllegalArgumentException e) {
                // polygon contains multiple instances of the same
                // IfcCartesianPoint
            }
            vertSizeIndex += 1 + vertSize;
        }

        IfcFacetedBrep faceSet = new IfcFacetedBrep(new IfcClosedShell(faces));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("Brep"),
                faceSet);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy faceSetProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(faceSetProxy);
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
        Set<IfcRepresentationItem> valveItems =
                new ValveBuilder(getSafeThickness(obj))
                        .addBottomOutput(obj.getRadius1(),
                                         obj.getLength1(),
                                         obj.getCrownRadius1(),
                                         obj.getCrownThickness1())
                        .addRightOutput(obj.getRadius2(),
                                        obj.getLength2(),
                                        obj.getCrownRadius2(),
                                        obj.getCrownThickness2()).addTopOutput(
                        obj.getRadius3(),
                        obj.getLength3(),
                        obj.getCrownRadius3(),
                        obj.getCrownThickness3())
                        .addLeftOutput(obj.getRadius4(),
                                       obj.getLength4(),
                                       obj.getCrownRadius4(),
                                       obj.getCrownThickness4()).build();

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowController valveProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(valveProduct);
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
        double poleRadius = obj.getRadius() / 4;
        double poleHeight = obj.getRadius() * 2;
        double discRadius = obj.getRadius();
        double discHeight = obj.getRadius() / 2;
        IfcAxis2Placement2D center = new IfcAxis2Placement2D(0, 0);

        IfcCircleProfileDef poleSection = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                center,
                new IfcPositiveLengthMeasure(poleRadius));
        IfcExtrudedAreaSolid pole = new IfcExtrudedAreaSolid(poleSection,
                                                             new IfcAxis2Placement3D(
                                                                     0,
                                                                     0,
                                                                     0),
                                                             new IfcDirection(0,
                                                                              0,
                                                                              1),
                                                             new IfcLengthMeasure(
                                                                     poleHeight));

        IfcCircleProfileDef discSection = new IfcCircleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                center,
                new IfcPositiveLengthMeasure(discRadius));
        IfcAxis2Placement3D discPosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              poleHeight +
                                                                      discRadius),
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcExtrudedAreaSolid disc = new IfcExtrudedAreaSolid(discSection,
                                                             discPosition,
                                                             new IfcDirection(0,
                                                                              0,
                                                                              1),
                                                             new IfcLengthMeasure(
                                                                     discHeight));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                pole,
                disc);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);

        IfcLocalPlacement instrProxyPlac = resolveLocation(obj);
        if (obj.getRotationArray() != null &&
                !Arrays.equals(obj.getRotationArray(),
                               new Double[]{0d, 0d, 0d}) &&
                !Arrays.equals(obj.getRotationArray(),
                               new Double[]{-0d, -0d, -0d})) {

            IfcAxis2Placement3D localCoordSys =
                    (IfcAxis2Placement3D) instrProxyPlac.getRelativePlacement();

            // rotating axis and refDirection of the Instrument's coordinate
            // system
            double[] axis = ifcToEywaVector(localCoordSys.getAxis()
                                                    .getNormalisedDirectionRatios()
                                                    .stream()
                                                    .mapToDouble(IfcReal::getValue)
                                                    .toArray(), false);
            double[] refDir = ifcToEywaVector(localCoordSys.getRefDirection()
                                                      .getNormalisedDirectionRatios()
                                                      .stream()
                                                      .mapToDouble(IfcReal::getValue)
                                                      .toArray(), false);
            rotate(axis, obj.getRotationArray(), "XYZ");
            rotate(refDir, obj.getRotationArray(), "XYZ");
            IfcDirection rotatedAxis = new IfcDirection(eywaToIfcVector(axis));
            IfcDirection rotatedRefDir =
                    new IfcDirection(eywaToIfcVector(refDir));

            // this doesn't modify the IfcAxis2Placement3D associated to obj
            // in objPositions, which must not be modified because it's used to
            // calculate the location of children of obj
            IfcAxis2Placement3D newLocalCoordSys = new IfcAxis2Placement3D(
                    localCoordSys.getLocation(),
                    rotatedAxis,
                    rotatedRefDir);
            instrProxyPlac =
                    new IfcLocalPlacement(instrProxyPlac.getPlacementRelTo(),
                                          newLocalCoordSys);
        }

        IfcDistributionControlElement instrumentProduct =
                IfcDistributionControlElement.builder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(instrProxyPlac)
                        .representation(productDefinitionShape).build();
        geometries.add(instrumentProduct);
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
        new IllegalArgumentException(
                "conversion of Ladder objects is currently not supported")
                .printStackTrace();
        resolveLocation(obj);
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
        IfcCartesianPoint[] vertices =
                new IfcCartesianPoint[obj.getVertices().length / 3];
        IntStream.range(0, vertices.length).forEach(i -> {
            int offset = i * 3;
            vertices[i] = new IfcCartesianPoint(obj.getVertices()[offset + 2],
                                                obj.getVertices()[offset],
                                                obj.getVertices()[offset + 1]);
        });

        Set<IfcFace> faces = new HashSet<>();

        int vertSizeIndex = 0;
        while (vertSizeIndex < obj.getFaces().length) {
            int vertSize = obj.getFaces()[vertSizeIndex] == 1 ? 4 : 3;
            IfcCartesianPoint[] polyVerts = new IfcCartesianPoint[vertSize];

            for (int i = 0, j = vertSizeIndex + 1; i < vertSize; i++, j++) {
                // i iterates on elements of polyVerts,
                // j iterates on the indices of vertices composing the polygon
                polyVerts[i] = vertices[obj.getFaces()[j]];
            }

            try {
                IfcPolyLoop polygon = new IfcPolyLoop(polyVerts);
                faces.add(new IfcFace(new IfcFaceBound(polygon, IfcBoolean.T)));
            } catch (IllegalArgumentException e) {
                // polygon contains multiple instances of the same
                // IfcCartesianPoint
            }
            vertSizeIndex += 1 + vertSize;
        }

        IfcFacetedBrep mesh = new IfcFacetedBrep(new IfcClosedShell(faces));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("Brep"),
                mesh);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        String name = obj.getRepresenting() == null ||
                obj.getRepresenting().equals("") ?
                obj.getClass().getSimpleName() : obj.getRepresenting();
        // TODO: use appropriate IfcProduct according to getRepresenting
        IfcProxy meshProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory).name(new IfcLabel(name))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(meshProxy);
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
        Set<IfcRepresentationItem> nozzleItems = new HashSet<>(5, 1);
        boolean hasTrunk =
                obj.getTrunkLength() != null && obj.getTrunkLength() != 0;
        boolean hasTang =
                obj.getTangLength() != null && obj.getTangLength() != 0;
        double raisedFaceLength = obj.getCrownThickness() / 10;
        double voidRadius = obj.getRadius() - getSafeThickness(obj);
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
                                                          getSafeThickness(obj)));
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
                                            voidRadius + getSafeThickness(obj),
                                            0),
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
        IfcFlowController nozzleProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(objectPlacement)
                        .representation(productDefinitionShape).build();
        geometries.add(nozzleProduct);
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
        ValveBuilder valveBuilder = new ValveBuilder(getSafeThickness(obj));
        if (obj.getWelded() != null && obj.getWelded()) {
            valveBuilder.addBottomOutput(obj.getRadius1(),
                                         obj.getLength1(),
                                         obj.getCrownRadius1(),
                                         obj.getCrownThickness1())
                    .addRightOutput(obj.getRadius2(),
                                    obj.getLength2(),
                                    obj.getCrownRadius2(),
                                    obj.getCrownThickness2());
        } else {
            valveBuilder
                    .addBottomOutput(obj.getRadius1(), obj.getLength1(), 0, 0)
                    .addRightOutput(obj.getRadius2(), obj.getLength2(), 0, 0);
        }

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveBuilder.build());
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowController valveProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(valveProduct);
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
        IfcAxis2Placement2D centre = new IfcAxis2Placement2D(0, 0);
        double blindThickness = getSafeThickness(obj);
        double plateThickness = getSafeThickness(obj) / 10;
        IfcRectangleProfileDef plateSection = new IfcRectangleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                centre,
                new IfcPositiveLengthMeasure(obj.getWidth()),
                new IfcPositiveLengthMeasure(obj.getDepth()));
        IfcExtrudedAreaSolid plate = new IfcExtrudedAreaSolid(plateSection,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      0),
                                                              new IfcDirection(0,
                                                                               0,
                                                                               1),
                                                              new IfcLengthMeasure(
                                                                      plateThickness));

        IfcRectangleProfileDef blindSection = new IfcRectangleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                centre,
                new IfcPositiveLengthMeasure(obj.getCrownWidth()),
                new IfcPositiveLengthMeasure(obj.getCrownDepth()));
        IfcExtrudedAreaSolid blind = new IfcExtrudedAreaSolid(blindSection,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      plateThickness),
                                                              new IfcDirection(0,
                                                                               0,
                                                                               1),
                                                              new IfcLengthMeasure(
                                                                      blindThickness));

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
            location = flip(location, blindThickness + plateThickness);
        }
        IfcDistributionFlowElement rectBlindProduct =
                IfcDistributionFlowElement.builder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape).build();
        geometries.add(rectBlindProduct);
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
        IfcAxis2Placement2D centre = new IfcAxis2Placement2D(0, 0);
        IfcDirection extrusionDirection = new IfcDirection(0, 0, 1);
        double length = obj.getLength() == null || obj.getLength() <= 0 ? 0 :
                obj.getLength();
        double endThickness =
                obj.getEndThickness() == null || obj.getEndThickness() <= 0 ?
                        0 : obj.getEndThickness();
        double neckLength;
        if (obj.getLength() == null) {
            neckLength = 0;
            length = endThickness;
        } else {
            neckLength = length - endThickness;
        }
        Set<IfcRepresentationItem> endplateItems =
                new HashSet<>(neckLength == 0 ? 2 : 3, 1);

        IfcRectangleProfileDef plateSection = new IfcRectangleProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                centre,
                new IfcPositiveLengthMeasure(obj.getWidth()),
                new IfcPositiveLengthMeasure(obj.getDepth()));
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
            IfcRectangleHollowProfileDef neckSection =
                    IfcRectangleHollowProfileDef.builder()
                            .profileType(IfcProfileTypeEnum.AREA)
                            .position(centre)
                            .xDim(new IfcPositiveLengthMeasure(obj.getWidth()))
                            .yDim(new IfcPositiveLengthMeasure(obj.getDepth()))
                            .wallThickness(new IfcPositiveLengthMeasure(
                                    getSafeThickness(obj))).build();
            IfcExtrudedAreaSolid neck = new IfcExtrudedAreaSolid(neckSection,
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
            location = flip(location, length);
        }
        IfcDistributionFlowElement rectEndplateProduct =
                IfcDistributionFlowElement.builder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(location)
                        .representation(productDefinitionShape).build();
        geometries.add(rectEndplateProduct);
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
        double neckLength =
                obj.getNeck() == null || obj.getNeck() <= 0 ? 0 : obj.getNeck();
        double length = obj.getLength() == null || obj.getLength() <= 0 ? 0 :
                obj.getLength();
        if (neckLength == 0 && length != 0) {
            neckLength = length - obj.getCrownThickness();
        }
        Set<IfcRepresentationItem> flangeItems =
                new HashSet<>(neckLength == 0 ? 2 : 3, 1);

        if (neckLength != 0) {
            IfcRectangleHollowProfileDef neckSection =
                    IfcRectangleHollowProfileDef.builder()
                            .profileType(IfcProfileTypeEnum.AREA)
                            .position(new IfcAxis2Placement2D(0, 0))
                            .xDim(new IfcPositiveLengthMeasure(obj.getWidth()))
                            .yDim(new IfcPositiveLengthMeasure(obj.getDepth()))
                            .wallThickness(new IfcPositiveLengthMeasure(
                                    getSafeThickness(obj))).build();
            IfcExtrudedAreaSolid neck = new IfcExtrudedAreaSolid(neckSection,
                                                                 new IfcAxis2Placement3D(
                                                                         0,
                                                                         0,
                                                                         0),
                                                                 extrusionDirection,
                                                                 new IfcLengthMeasure(
                                                                         neckLength));
            flangeItems.add(neck);
        }

        double halfInnerCrownWidth = obj.getWidth() / 2 - getSafeThickness(obj);
        double halfInnerCrownDepth = obj.getDepth() / 2 - getSafeThickness(obj);
        IfcPolyline innerNeckSection = new IfcPolyline(new IfcCartesianPoint(
                halfInnerCrownWidth,
                halfInnerCrownDepth),
                                                       new IfcCartesianPoint(-halfInnerCrownWidth,
                                                                             halfInnerCrownDepth),
                                                       new IfcCartesianPoint(-halfInnerCrownWidth,
                                                                             -halfInnerCrownDepth),
                                                       new IfcCartesianPoint(
                                                               halfInnerCrownWidth,
                                                               -halfInnerCrownDepth));
        double halfOuterCrownWidth = halfInnerCrownWidth + obj.getCrownWidth();
        double halfOuterCrownDepth = halfInnerCrownDepth + obj.getCrownDepth();
        IfcPolyline outerCrownSection = new IfcPolyline(new IfcCartesianPoint(
                halfOuterCrownWidth,
                halfOuterCrownDepth),
                                                        new IfcCartesianPoint(-halfOuterCrownWidth,
                                                                              halfOuterCrownDepth),
                                                        new IfcCartesianPoint(-halfOuterCrownWidth,
                                                                              -halfOuterCrownDepth),
                                                        new IfcCartesianPoint(
                                                                halfOuterCrownWidth,
                                                                -halfOuterCrownDepth));
        IfcArbitraryProfileDefWithVoids crownSection =
                new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum.AREA,
                                                    null,
                                                    outerCrownSection,
                                                    innerNeckSection);
        IfcExtrudedAreaSolid crown = new IfcExtrudedAreaSolid(crownSection,
                                                              new IfcAxis2Placement3D(
                                                                      0,
                                                                      0,
                                                                      neckLength),
                                                              extrusionDirection,
                                                              new IfcLengthMeasure(
                                                                      obj.getCrownThickness()));
        flangeItems.add(crown);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                flangeItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowController rectFlangeProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(rectFlangeProduct);
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
                                                                      getSafeThickness(
                                                                              obj)));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                plate);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowFitting rectPlateProduct = IfcFlowFitting.flowFittingBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(rectPlateProduct);
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
        IfcRectangleHollowProfileDef section =
                IfcRectangleHollowProfileDef.builder()
                        .profileType(IfcProfileTypeEnum.AREA)
                        .position(new IfcAxis2Placement2D(0, 0))
                        .xDim(new IfcPositiveLengthMeasure(obj.getWidth()))
                        .yDim(new IfcPositiveLengthMeasure(obj.getDepth()))
                        .wallThickness(new IfcPositiveLengthMeasure(
                                getSafeThickness(obj))).build();
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
        IfcFlowSegment rectShellProduct = IfcFlowSegment.flowSegmentBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(rectShellProduct);
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
                                                                     getSafeThickness(
                                                                             obj)));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                ring);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowFitting ringProduct = IfcFlowFitting.flowFittingBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(ringProduct);
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
                obj.getRadius1() - getSafeThickness(obj), 0),
                                                new IfcCartesianPoint(obj.getRadius1(),
                                                                      0),
                                                new IfcCartesianPoint(obj.getRadius2(),
                                                                      obj.getLength()),
                                                new IfcCartesianPoint(
                                                        obj.getRadius2() -
                                                                getSafeThickness(
                                                                        obj),
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
        IfcFlowSegment shellProduct = IfcFlowSegment.flowSegmentBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(shellProduct);
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
        IfcCircleProfileDef circle =
                new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                                        null,
                                        new IfcAxis2Placement2D(0, 0),
                                        new IfcPositiveLengthMeasure(obj.getRadius()));
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0));
        IfcRevolvedAreaSolid sphere = new IfcRevolvedAreaSolid(circle,
                                                               new IfcAxis2Placement3D(
                                                                       0,
                                                                       0,
                                                                       0),
                                                               rotationAxis,
                                                               new IfcPlaneAngleMeasure(
                                                                       PI));
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                sphere);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy sphereProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(sphereProxy);
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
        new IllegalArgumentException(
                "conversion of Stair objects is currently not supported")
                .printStackTrace();
        resolveLocation(obj);
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
        new IllegalArgumentException(
                "conversion of Sweep objects is currently not supported")
                .printStackTrace();
        resolveLocation(obj);
        //IfcArbitraryClosedProfileDef shape;
        //if (obj.getCurves().length > 1) {
        //    shape = new IfcArbitraryProfileDefWithVoids(IfcProfileTypeEnum
        //    .AREA,
        //                                                null,
        //                                                outerCurve,
        //                                                innerCurves);
        //} else {
        //    shape = new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
        //                                             null,
        //                                             outerCurve);
        //}
        //IfcExtrudedAreaSolid sweep = new IfcExtrudedAreaSolid(shape,
        //                                                      new
        //                                                      IfcAxis2Placement3D(
        //                                                              0,
        //                                                              0,
        //                                                              0),
        //                                                      new
        //                                                      IfcDirection(0,
        //                                                                       0,
        //                                                                       1),
        //                                                      new
        //                                                      IfcLengthMeasure(
        //                                                              getSafeThickness(
        //                                                                      obj)));
        ////  cut sweep if n2 is not parallel to the extrusion direction.
        ////  If this is the case, direction should be longer to allow for
        ////  uniform cutting

        //IfcShapeRepresentation shapeRepresentation = new
        // IfcShapeRepresentation(
        //        GEOMETRIC_REPRESENTATION_CONTEXT,
        //        new IfcLabel("Body"),
        //        new IfcLabel("SweptSolid"),
        //        sweep);
        //IfcProductDefinitionShape productDefinitionShape =
        //        new IfcProductDefinitionShape(null, null,
        //        shapeRepresentation);
        //IfcProxy sweepProxy =
        //        IfcProxy.builder().globalId(new IfcGloballyUniqueId())
        //                .ownerHistory(ownerHistory)
        //                .name(new IfcLabel(obj.getClass().getSimpleName()))
        //                .description(new IfcText(getDescription(obj)))
        //                .objectPlacement(resolveLocation(obj))
        //                .representation(productDefinitionShape)
        //                .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        //geometries.add(sweepProxy);
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
        IfcCircleHollowProfileDef shellSection = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                new IfcAxis2Placement2D(0, 0),
                new IfcPositiveLengthMeasure(obj.getRadius()),
                new IfcPositiveLengthMeasure(getSafeThickness(obj)));
        IfcExtrudedAreaSolid tankShell = new IfcExtrudedAreaSolid(shellSection,
                                                                  new IfcAxis2Placement3D(
                                                                          0,
                                                                          0,
                                                                          0),
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
                                                                  new IfcLengthMeasure(
                                                                          obj.getHeight()));

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                tankShell);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowStorageDevice tankShellProduct =
                IfcFlowStorageDevice.flowStorageDeviceBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(tankShellProduct);
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
                        obj.getRadius1() - getSafeThickness(obj)));
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
                        obj.getRadius2() - getSafeThickness(obj)));
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
                obj.getDerivationThickness() == null ? getSafeThickness(obj) :
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
        IfcFlowFitting teeProduct = IfcFlowFitting.flowFittingBuilder()
                .globalId(new IfcGloballyUniqueId()).ownerHistory(ownerHistory)
                .name(new IfcLabel(obj.getClass().getSimpleName()))
                .description(new IfcText(getDescription(obj)))
                .objectPlacement(resolveLocation(obj))
                .representation(productDefinitionShape).build();
        geometries.add(teeProduct);
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
        Set<IfcRepresentationItem> valveItems =
                new ValveBuilder(getSafeThickness(obj))
                        .addBottomOutput(obj.getRadius1(),
                                         obj.getLength1(),
                                         obj.getCrownRadius1(),
                                         obj.getCrownThickness1())
                        .addRightOutput(obj.getRadius2(),
                                        obj.getLength2(),
                                        obj.getCrownRadius2(),
                                        obj.getCrownThickness2()).addTopOutput(
                        obj.getRadius3(),
                        obj.getLength3(),
                        obj.getCrownRadius3(),
                        obj.getCrownThickness3()).build();

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveItems);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowController valveProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(valveProduct);
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
        ValveBuilder valveBuilder = new ValveBuilder(getSafeThickness(obj));
        if (obj.getFlanged() != null && obj.getFlanged()) {
            valveBuilder.addBottomOutput(obj.getRadius1(),
                                         obj.getLength1(),
                                         obj.getCrownRadius1(),
                                         obj.getCrownThickness1()).addTopOutput(
                    obj.getRadius2(),
                    obj.getLength2(),
                    obj.getCrownRadius2(),
                    obj.getCrownThickness2());
        } else {
            valveBuilder
                    .addBottomOutput(obj.getRadius1(), obj.getLength1(), 0, 0)
                    .addTopOutput(obj.getRadius2(), obj.getLength2(), 0, 0);
        }

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("CSG"),
                valveBuilder.build());
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcFlowController valveProduct =
                IfcFlowController.flowControllerBuilder()
                        .globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(resolveLocation(obj))
                        .representation(productDefinitionShape).build();
        geometries.add(valveProduct);
    }
}
