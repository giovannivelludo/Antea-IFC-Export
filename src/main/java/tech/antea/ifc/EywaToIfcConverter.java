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

import static java.lang.Math.PI;

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
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0, 0, 1),
                                        new IfcDirection(1, 0, 0));
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
     * @param unflipped The placement of the object to flip.
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

        // this doesn't change the IfcAxis2Placement3D associated to obj
        // in objPositions, which is used to calculate the location of
        // children of obj
        IfcAxis2Placement3D newLocalCoordSys = new IfcAxis2Placement3D(
                shiftedOrigin,
                flippedAxis,
                localCoordSys.getRefDirection());
        return new IfcLocalPlacement(unflipped.getPlacementRelTo(),
                                     newLocalCoordSys);
    }

    /**
     * @param values The array to read.
     * @return The minimum value in the array.
     *
     * @throws NullPointerException     If values is null.
     * @throws IllegalArgumentException If values is empty.
     */
    private static double min(@NonNull double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("values is empty");
        }
        double min = values[0];
        for (double value : values) {
            if (value < min) {
                min = value;
            }
        }
        return min;
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
            parentPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
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
        Set<IfcRepresentationItem> blindItems = new HashSet<>(2);
        if (obj.getCrownRadius() == null) {
            // there's no plate
            IfcAxis2Placement2D basePlacement =
                    new IfcAxis2Placement2D(new IfcCartesianPoint(0, 0),
                                            new IfcDirection(1, 0));
            IfcCircleProfileDef blindBase = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    basePlacement,
                    new IfcPositiveLengthMeasure(obj.getRadius()));
            IfcAxis2Placement3D blindPlacement =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid blind = new IfcExtrudedAreaSolid(blindBase,
                                                                  blindPlacement,
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
                                                                  new IfcLengthMeasure(
                                                                          obj.getCrownThickness()));
            blindItems.add(blind);
        } else {
            // TODO: find how to calculate plateThickness
            double plateThickness = obj.getCrownThickness() / 5;
            // parameters of the bottom cylinder, usually the disc, but when
            // switched == true it's the plate
            double bottomThickness;
            double bottomRadius;
            // parameters of the top cylinder, which is usually the plate, but
            // when switched == true it's the disc
            double topThickness;
            double topRadius;
            if (!obj.isSwitched()) {
                topThickness = plateThickness;
                topRadius = obj.getRadius();
                bottomThickness = obj.getCrownThickness();
                bottomRadius = obj.getCrownRadius();
            } else {
                topThickness = obj.getCrownThickness();
                topRadius = obj.getCrownRadius();
                bottomThickness = plateThickness;
                bottomRadius = obj.getRadius();
            }
            IfcAxis2Placement2D cylinderBasePlacement = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));

            IfcCircleProfileDef bottomBase = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    cylinderBasePlacement,
                    new IfcPositiveLengthMeasure(bottomRadius));
            IfcAxis2Placement3D bottomCylinderPlacement =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid bottomCylinder = new IfcExtrudedAreaSolid(
                    bottomBase,
                    bottomCylinderPlacement,
                    new IfcDirection(0, 0, 1),
                    new IfcLengthMeasure(bottomThickness));

            IfcCircleProfileDef topBase = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    cylinderBasePlacement,
                    new IfcPositiveLengthMeasure(topRadius));
            IfcAxis2Placement3D topCylinderPlacement =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  bottomThickness),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid topCylinder = new IfcExtrudedAreaSolid(topBase,
                                                                        topCylinderPlacement,
                                                                        new IfcDirection(
                                                                                0,
                                                                                0,
                                                                                1),
                                                                        new IfcLengthMeasure(
                                                                                topThickness));
            blindItems.add(bottomCylinder);
            blindItems.add(topCylinder);
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
                        .objectPlacement(resolveLocation(obj))
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
        IfcLocalPlacement objPlacement = resolveLocation(obj);
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
                new IfcAxis2Placement2D(new IfcCartesianPoint(0,
                                                              obj.getCurveRadius()),
                                        new IfcDirection(1, 0));
        IfcCircleHollowProfileDef profile = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                profilePosition,
                new IfcPositiveLengthMeasure(radius),
                new IfcPositiveLengthMeasure(obj.getThickness()));
        IfcAxis2Placement3D curvePlacement =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0, 0, 1),
                                        new IfcDirection(1, 0, 0));
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
                        .objectPlacement(objPlacement)
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
            IfcAxis2Placement2D neckSectionPosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcCircleHollowProfileDef neckSection =
                    new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                                  null,
                                                  neckSectionPosition,
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getRadius()),
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getThickness()));
            IfcAxis2Placement3D neckPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            neck = new IfcExtrudedAreaSolid(neckSection,
                                            neckPosition,
                                            new IfcDirection(0, 0, 1),
                                            new IfcLengthMeasure(obj.getNeck()));
        }

        double semiAxis2 = obj.getDish() != null ? obj.getDish() :
                obj.getCambering() * obj.getRadius();
        if (semiAxis2 == 0) {
            IfcAxis2Placement2D plateSectionPosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcCircleProfileDef plateSection = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    plateSectionPosition,
                    new IfcPositiveLengthMeasure(obj.getRadius()));
            IfcAxis2Placement3D platePosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  obj.getNeck()),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
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
            IfcAxis2Placement2D outerEllipsePosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcEllipseProfileDef outerEllipse = new IfcEllipseProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    outerEllipsePosition,
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

            IfcAxis2Placement2D innerEllipsePosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcEllipseProfileDef innerEllipse = new IfcEllipseProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    innerEllipsePosition,
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
                    new IfcPlane(new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                               0,
                                                                               obj.getNeck()),
                                                         new IfcDirection(0,
                                                                          0,
                                                                          1),
                                                         new IfcDirection(1,
                                                                          0,
                                                                          0)));
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
        Set<IfcRepresentationItem> nozzleItems = new HashSet<>(4);
        boolean hasTrunk =
                obj.getTrunkLength() != null && obj.getTrunkLength() != 0;
        boolean hasTang =
                obj.getTangLength() != null && obj.getTangLength() != 0;
        double raisedFaceLength = obj.getCrownThickness() / 10;
        double voidRadius = obj.getRadius() - obj.getThickness();
        double raisedFaceRadius =
                obj.getRadius() + (obj.getCrownRadius() - obj.getRadius()) / 3;

        if (hasTrunk) {
            IfcAxis2Placement2D trunkSectionPosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcCircleHollowProfileDef trunkSection =
                    new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                                  null,
                                                  trunkSectionPosition,
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getRadius()),
                                                  new IfcPositiveLengthMeasure(
                                                          obj.getThickness()));
            IfcAxis2Placement3D trunkPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid trunk = new IfcExtrudedAreaSolid(trunkSection,
                                                                  trunkPosition,
                                                                  new IfcDirection(
                                                                          0,
                                                                          0,
                                                                          1),
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

        IfcAxis2Placement2D crownSectionPosition =
                new IfcAxis2Placement2D(new IfcCartesianPoint(0, 0),
                                        new IfcDirection(1, 0));
        IfcCircleHollowProfileDef crownSection = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                crownSectionPosition,
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
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              crownZOffset),
                                        new IfcDirection(0, 0, 1),
                                        new IfcDirection(1, 0, 0));
        IfcExtrudedAreaSolid crown = new IfcExtrudedAreaSolid(crownSection,
                                                              crownPosition,
                                                              new IfcDirection(0,
                                                                               0,
                                                                               1),
                                                              new IfcLengthMeasure(
                                                                      obj.getCrownThickness()));
        nozzleItems.add(crown);

        IfcAxis2Placement2D raisedFaceSectionPosition = new IfcAxis2Placement2D(
                new IfcCartesianPoint(0, 0),
                new IfcDirection(1, 0));
        IfcCircleHollowProfileDef raisedFaceSection =
                new IfcCircleHollowProfileDef(IfcProfileTypeEnum.AREA,
                                              null,
                                              raisedFaceSectionPosition,
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
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              raisedFaceZOffset),
                                        new IfcDirection(0, 0, 1),
                                        new IfcDirection(1, 0, 0));
        IfcExtrudedAreaSolid raisedFace = new IfcExtrudedAreaSolid(
                raisedFaceSection,
                raisedFacePosition,
                new IfcDirection(0, 0, 1),
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
        double thickness =
                obj.getThickness() == 0d || obj.getThickness() == -0d ? 0.1 :
                        obj.getThickness();
        Set<IfcRepresentationItem> valveItems = new HashSet<>(4);
        // creating the first ouput
        IfcPolyline outerTriangle1 =
                new IfcPolyline(new IfcCartesianPoint(0, 0),
                                new IfcCartesianPoint(obj.getRadius1(), 0),
                                new IfcCartesianPoint(0, obj.getLength1()));
        IfcArbitraryClosedProfileDef outerCone1Section =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 outerTriangle1);
        IfcAxis2Placement3D outerCone1Position =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcAxis1Placement rotationAxis =
                new IfcAxis1Placement(new IfcCartesianPoint(0, 0, 0),
                                      new IfcDirection(0, 1, 0));
        IfcRevolvedAreaSolid outerCone1 = new IfcRevolvedAreaSolid(
                outerCone1Section,
                outerCone1Position,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        double innerRadius1 = obj.getRadius1() - thickness;
        IfcPolyline innerTriangle1 =
                new IfcPolyline(new IfcCartesianPoint(0, 0),
                                new IfcCartesianPoint(innerRadius1, 0),
                                new IfcCartesianPoint(0,
                                                      (obj.getLength1() /
                                                              obj.getRadius1()) *
                                                              innerRadius1));
        IfcArbitraryClosedProfileDef innerCone1Section =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 innerTriangle1);
        IfcAxis2Placement3D innerCone1Position =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid innerCone1 = new IfcRevolvedAreaSolid(
                innerCone1Section,
                innerCone1Position,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        IfcBooleanResult output1 =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     outerCone1,
                                     innerCone1);
        valveItems.add(output1);

        // creating the second output
        IfcPolyline outerTriangle2 =
                new IfcPolyline(new IfcCartesianPoint(0, 0),
                                new IfcCartesianPoint(obj.getRadius2(), 0),
                                new IfcCartesianPoint(0, obj.getLength2()));
        IfcArbitraryClosedProfileDef outerCone2Section =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 outerTriangle2);
        IfcAxis2Placement3D outerCone2Position =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              obj.getLength1() +
                                                                      obj.getLength2()),
                                        new IfcDirection(0, 1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid outerCone2 = new IfcRevolvedAreaSolid(
                outerCone2Section,
                outerCone2Position,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        double innerRadius2 = obj.getRadius2() - thickness;
        IfcPolyline innerTriangle2 =
                new IfcPolyline(new IfcCartesianPoint(0, 0),
                                new IfcCartesianPoint(innerRadius2, 0),
                                new IfcCartesianPoint(0,
                                                      (obj.getLength2() /
                                                              obj.getRadius2()) *
                                                              innerRadius2));
        IfcArbitraryClosedProfileDef innerCone2Section =
                new IfcArbitraryClosedProfileDef(IfcProfileTypeEnum.AREA,
                                                 null,
                                                 innerTriangle2);
        IfcAxis2Placement3D innerCone2Position =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              obj.getLength1() +
                                                                      obj.getLength2()),
                                        new IfcDirection(0, 1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid innerCone2 = new IfcRevolvedAreaSolid(
                innerCone2Section,
                innerCone2Position,
                rotationAxis,
                new IfcPlaneAngleMeasure(2 * PI));

        IfcBooleanResult output2 =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     outerCone2,
                                     innerCone2);
        valveItems.add(output2);

        // creating the sphere
        IfcAxis2Placement2D circlePosition =
                new IfcAxis2Placement2D(new IfcCartesianPoint(0, 0),
                                        new IfcDirection(1, 0));
        IfcPositiveLengthMeasure circleRadius =
                new IfcPositiveLengthMeasure(min(obj.getLength1(),
                                                 obj.getLength2(),
                                                 obj.getRadius1(),
                                                 obj.getRadius2()));
        IfcCircleProfileDef circle =
                new IfcCircleProfileDef(IfcProfileTypeEnum.AREA,
                                        null,
                                        circlePosition,
                                        circleRadius);
        IfcAxis2Placement3D spherePosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                              0,
                                                              obj.getLength1()),
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid sphere = new IfcRevolvedAreaSolid(circle,
                                                               spherePosition,
                                                               rotationAxis,
                                                               new IfcPlaneAngleMeasure(
                                                                       PI));
        IfcBooleanResult cutSphere =
                new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                     sphere,
                                     new IfcBooleanResult(IfcBooleanOperator.UNION,
                                                          outerCone1,
                                                          outerCone2));
        valveItems.add(cutSphere);

        if (obj.getFlanged() != null && obj.getFlanged().equals(true)) {
            IfcAxis2Placement2D crownSectionPosition = new IfcAxis2Placement2D(
                    new IfcCartesianPoint(0, 0),
                    new IfcDirection(1, 0));
            IfcCircleProfileDef crownSection1 = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    crownSectionPosition,
                    new IfcPositiveLengthMeasure(obj.getCrownRadius1()));
            IfcAxis2Placement3D crownPosition1 =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid crown1 =
                    new IfcExtrudedAreaSolid(crownSection1,
                                             crownPosition1,
                                             new IfcDirection(0, 0, 1),
                                             new IfcLengthMeasure(obj.getCrownThickness1()));
            IfcBooleanResult flange1 =
                    new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                         crown1,
                                         outerCone1);

            IfcCircleProfileDef crownSection2 = new IfcCircleProfileDef(
                    IfcProfileTypeEnum.AREA,
                    null,
                    crownSectionPosition,
                    new IfcPositiveLengthMeasure(obj.getCrownRadius2()));
            IfcAxis2Placement3D crownPosition2 =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0,
                                                                  0,
                                                                  obj.getLength1() +
                                                                          obj.getLength2() -
                                                                          obj.getCrownThickness2()),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
            IfcExtrudedAreaSolid crown2 =
                    new IfcExtrudedAreaSolid(crownSection2,
                                             crownPosition2,
                                             new IfcDirection(0, 0, 1),
                                             new IfcLengthMeasure(obj.getCrownThickness2()));
            IfcBooleanResult flange2 =
                    new IfcBooleanResult(IfcBooleanOperator.DIFFERENCE,
                                         crown2,
                                         outerCone2);

            valveItems.add(flange1);
            valveItems.add(flange2);
        }

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
}
