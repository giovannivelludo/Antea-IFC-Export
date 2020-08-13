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
import it.imc.persistence.po.eytukan.*;
import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.Math.*;

public class EywaToIfcConverter implements EywaConverter {

    private static final String COMPANY_NAME = "Antea";
    private static final String PROGRAM_NAME = "Antea IFC Export";
    private static final String PROGRAM_VERSION = "0.0.1-SNAPSHOT";
    private static final String PROGRAM_ID = "com.anteash:ifc";

    /**
     * Owner history for all {@link IfcRoot} objects in this project.
     */
    private final IfcOwnerHistory ownerHistory;
    private final IfcGeometricRepresentationContext
            geometricRepresentationContext;
    /**
     * The set containing the conversion of all Eywa geometries.
     */
    private final Set<IfcProduct> geometries;
    /**
     * Maps each Primitive in the Eywa tree to its position relative to the
     * world coordinate system.
     */
    private final Map<Primitive, IfcAxis2Placement3D> objPositions;
    private Map<String, Object> hints;

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
        IfcAxis2Placement3D worldCoordinateSystem =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(0, 0, 1),
                                        new IfcDirection(1, 0, 0));
        this.geometricRepresentationContext =
                new IfcGeometricRepresentationContext(new IfcLabel("Plan"),
                                                      new IfcLabel("Model"),
                                                      new IfcDimensionCount(3),
                                                      //TODO: decide what
                                                      // precision to use
                                                      new IfcReal(1.E-05),
                                                      worldCoordinateSystem,
                                                      new IfcDirection(0,
                                                                       1,
                                                                       0));

        this.geometries = new HashSet<>();
        this.objPositions = new HashMap<>();
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
        Header header = new Header().setAuthor(COMPANY_NAME)
                .setDescription("ViewDefinition [CoordinationView]")
                .setOrganization(COMPANY_NAME)
                .setOriginatingSystem(PROGRAM_NAME + " " + PROGRAM_VERSION);
        new Serializer().serialize(header, project, output);
    }

    /**
     * @param matrix An array representing a 4x4 matrix in row-major order.
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
        for (int i = 0; i < 4; i++) { // iterates on rows of matrix
            result[i] = 0;
            for (int j = 0; j < 4; j++) { // iterates on columns of matrix
                result[i] += matrix[i * 4 + j] * vector[j];
            }
        }
        return result;
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
                        .representationContext(geometricRepresentationContext)
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
     * Computes the location of {@code obj} and adds it to {@code objPosition}.
     *
     * @param obj The object of which to compute the absolute location.
     * @return The location of the object relative to the world coordinate
     * system.
     *
     * @throws NullPointerException     If obj is {@code null}.
     * @throws IllegalArgumentException If {@code obj} doesn't have a matrix
     *                                  field.
     */
    private IfcAxis2Placement3D computeLocation(@NonNull Primitive obj) {
        Double[] matrix = obj.getMatrix();
        if (matrix == null) {
            throw new IllegalArgumentException(
                    "Only conversion of objects whose position is defined" +
                            " with a matrix is supported at the moment");
            // TODO: implement conversion of Primitives using position and
            //  rotation
        }
        IfcAxis2Placement3D parentPosition = objPositions.get(obj.getParent());
        if (parentPosition == null) {
            // obj is the root object
            parentPosition =
                    new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                            new IfcDirection(0, 0, 1),
                                            new IfcDirection(1, 0, 0));
        }
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
        System.arraycopy(multiply(matrix, refDir), 0, newRefDirection, 0, 3);
        IfcAxis2Placement3D objPosition =
                new IfcAxis2Placement3D(new IfcCartesianPoint(newLocation),
                                        new IfcDirection(newAxis),
                                        new IfcDirection(newRefDirection));
        objPositions.put(obj, objPosition);
        return objPosition;
    }

    /**
     * @param obj The {@link Beam} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Beam obj) {

    }

    /**
     * @param obj The {@link Blind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Blind obj) {

    }

    /**
     * @param obj The {@link Box} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Box obj) {

    }

    /**
     * @param obj The {@link Collar} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Collar obj) {

    }

    /**
     * @param obj The {@link Curve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Curve obj) {

    }

    /**
     * @param obj The {@link Dielectric} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Dielectric obj) {

    }

    /**
     * @param obj The {@link Dish} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Dish obj) {

    }

    /**
     * @param obj The {@link DualExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(DualExpansionJoint obj) {

    }

    /**
     * @param obj The {@link EccentricCone} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(EccentricCone obj) {

    }

    /**
     * @param obj The {@link Empty} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Empty obj) {
        computeLocation(obj);
    }

    /**
     * @param obj The {@link Endplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Endplate obj) {

    }

    /**
     * @param obj The {@link ExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(ExpansionJoint obj) {

    }

    /**
     * @param obj The {@link FaceSet} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(FaceSet obj) {

    }

    /**
     * @param obj The {@link FourWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(FourWaysValve obj) {

    }

    /**
     * @param obj The {@link Instrument} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Instrument obj) {

    }

    /**
     * @param obj The {@link Ladder} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Ladder obj) {

    }

    /**
     * @param obj The {@link Mesh} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Mesh obj) {

    }

    /**
     * @param obj The {@link Nozzle} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Nozzle obj) {

    }

    /**
     * @param obj The {@link OrthoValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(OrthoValve obj) {

    }

    /**
     * @param obj The {@link RectangularBlind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(RectangularBlind obj) {

    }

    /**
     * @param obj The {@link RectangularEndplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(RectangularEndplate obj) {

    }

    /**
     * @param obj The {@link RectangularFlange} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(RectangularFlange obj) {

    }

    /**
     * @param obj The {@link RectangularPlate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(RectangularPlate obj) {

    }

    /**
     * @param obj The {@link RectangularShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(RectangularShell obj) {

    }

    /**
     * @param obj The {@link Ring} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Ring obj) {

    }

    /**
     * @param obj The {@link Shell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Shell obj) {
        IfcAxis2Placement3D objPosition = computeLocation(obj);
        //TODO: use BigDecimal to get precise results?
        //creating a parallelogram that is the vertical section of the shell
        double height = obj.getLength();
        double radiusDifference = obj.getRadius1() - obj.getRadius2();
        double side =
                sqrt((height * height) + (radiusDifference * radiusDifference));
        // alfa is the bottom right angle of the parallelogram
        double sinAlfa = height / side;
        double base = obj.getThickness() / sinAlfa;
        double topBaseOffset;
        if (obj.getRadius2() < obj.getRadius1()) {
            // if the top base is shifted left compared to the bottom base,
            // the offset must be negative
            topBaseOffset = -sqrt(1 - (sinAlfa * sinAlfa)) *
                    side; // == -cos(asin(sinAlfa)) * side
        } else if (obj.getRadius2().equals(obj.getRadius1())) {
            topBaseOffset = 0;
        } else {
            // the top base is shifted right compared to the bottom base, the
            // offset must be positive
            topBaseOffset =
                    sinAlfa * side; // == -cos((PI / 2) + asin(sinAlfa)) * side
        }
        // the parallelogram will be rotated around the y axis and the center
        // of its bounding box is currently on the origin of the xy plane, so
        // it must be shifted to the top right
        double minRadius = min(obj.getRadius2(), obj.getRadius1());
        IfcAxis2Placement2D sweptAreaPlacement =
                new IfcAxis2Placement2D(new IfcCartesianPoint(
                        -minRadius + (base / 2) - (abs(topBaseOffset) / 2),
                        -height / 2), new IfcDirection(1, 0));

        IfcTrapeziumProfileDef sweptArea = new IfcTrapeziumProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                sweptAreaPlacement,
                new IfcPositiveLengthMeasure(base),
                new IfcPositiveLengthMeasure(base),
                new IfcPositiveLengthMeasure(height),
                new IfcLengthMeasure(topBaseOffset));
        IfcAxis2Placement3D shellPlacement =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        // the z axis is rotated by PI/2
                                        // towards the negative y axis, and
                                        // the y axis becomes the vertical axis
                                        new IfcDirection(0, -1, 0),
                                        new IfcDirection(1, 0, 0));
        IfcRevolvedAreaSolid shell = new IfcRevolvedAreaSolid(sweptArea,
                                                              shellPlacement,
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
                geometricRepresentationContext,
                new IfcLabel("Body"),
                new IfcLabel("SweptSolid"),
                shell);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy shellProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .objectPlacement(new IfcLocalPlacement(null,
                                                               objPosition))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        //TODO: instead of IfcProxy, use a suitable IfcProduct for each Eywa
        // primitive (when possible)
        geometries.add(shellProxy);
    }

    /**
     * @param obj The {@link Sphere} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Sphere obj) {

    }

    /**
     * @param obj The {@link Stair} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Stair obj) {

    }

    /**
     * @param obj The {@link Sweep} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Sweep obj) {

    }

    /**
     * @param obj The {@link TankShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(TankShell obj) {

    }

    /**
     * @param obj The {@link Tee} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Tee obj) {

    }

    /**
     * @param obj The {@link ThreeWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(ThreeWaysValve obj) {

    }

    /**
     * @param obj The {@link Valve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(Valve obj) {

    }
}
