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
     * Resolves the location of {@code obj} and adds it to {@code objPosition}.
     *
     * @param obj The object of which to resolve the absolute location.
     * @return The location of the object relative to the world coordinate
     * system.
     *
     * @throws NullPointerException     If obj is {@code null}.
     * @throws IllegalArgumentException If in {@code obj} the matrix field is
     *                                  not set and the rotation field is.
     */
    private IfcAxis2Placement3D resolveLocation(@NonNull Primitive obj) {
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
            // TODO: implement conversion of Primitives using rotation
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
        return objPosition;
    }

    /**
     * @param obj The {@link Beam} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Beam obj) {

    }

    /**
     * @param obj The {@link Blind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Blind obj) {
        IfcAxis2Placement3D objPosition = resolveLocation(obj);
        IfcRepresentationItem blind;
        IfcLabel representationType;
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
            blind = new IfcExtrudedAreaSolid(blindBase,
                                             blindPlacement,
                                             new IfcDirection(0, 0, 1),
                                             new IfcLengthMeasure(obj.getCrownThickness()));
            representationType = new IfcLabel("SweptSolid");
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

            blind = new IfcBooleanResult(IfcBooleanOperator.UNION,
                                         bottomCylinder,
                                         topCylinder);
            representationType = new IfcLabel("CSG");
        }
        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                representationType,
                blind);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy blindProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(new IfcLocalPlacement(null,
                                                               objPosition))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(blindProxy);
    }

    /**
     * @param obj The {@link Box} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Box obj) {

    }

    /**
     * @param obj The {@link Collar} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Collar obj) {

    }

    /**
     * @param obj The {@link Curve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Curve obj) {
        IfcAxis2Placement3D objPosition = resolveLocation(obj);
        Double radius1 = obj.getRadius1();
        Double radius2 = obj.getRadius2();
        if (obj.getRadius() != null) {
            radius1 = obj.getRadius();
            radius2 = obj.getRadius();
        }
        double wallThickness = obj.getThickness() + obj.getLining();

        //creating the spineCurve
        IfcAxis2Placement3D circumferencePlacement =
                new IfcAxis2Placement3D(new IfcCartesianPoint(0, 0, 0),
                                        new IfcDirection(1, 0, 0),
                                        new IfcDirection(0, 1, 0));
        IfcCircle circumference = new IfcCircle(circumferencePlacement,
                                                new IfcPositiveLengthMeasure(obj.getCurveRadius()));
        Set<IfcTrimmingSelect> trim1 =
                Collections.singleton(new IfcParameterValue(0));
        Set<IfcTrimmingSelect> trim2 =
                Collections.singleton(new IfcParameterValue(obj.getAngle()));
        IfcTrimmedCurve parentCurve = new IfcTrimmedCurve(circumference,
                                                          trim1,
                                                          trim2,
                                                          new IfcBoolean(true),
                                                          IfcTrimmingPreference.PARAMETER);
        IfcCompositeCurveSegment segment = new IfcCompositeCurveSegment(
                IfcTransitionCode.DISCONTINUOUS,
                true,
                parentCurve);
        List<IfcCompositeCurveSegment> segments =
                Collections.singletonList(segment);
        IfcCompositeCurve spineCurve =
                new IfcCompositeCurve(segments, new IfcLogical(false));

        // creating the two crossSections
        IfcAxis2Placement2D circlePosition =
                new IfcAxis2Placement2D(new IfcCartesianPoint(0, 0),
                                        new IfcDirection(1, 0));
        IfcCircleHollowProfileDef circle1 = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                circlePosition,
                new IfcPositiveLengthMeasure(radius1),
                new IfcPositiveLengthMeasure(wallThickness));
        IfcCircleHollowProfileDef circle2 = new IfcCircleHollowProfileDef(
                IfcProfileTypeEnum.AREA,
                null,
                circlePosition,
                new IfcPositiveLengthMeasure(radius2),
                new IfcPositiveLengthMeasure(wallThickness));
        List<IfcProfileDef> crossSections = Arrays.asList(circle1, circle2);

        // creating the two crossSectionPositions
        //TODO: right now cartesian points and direction are given relatively
        // to circumferencePlacement, check if that's right or if they should be
        // given relative to the placement of the object
        IfcAxis2Placement3D position1 =
                new IfcAxis2Placement3D(new IfcCartesianPoint(obj.getCurveRadius(),
                                                              0,
                                                              0),
                                        new IfcDirection(0, 1, 0),
                                        new IfcDirection(0, 0, 1));
        // angolo asse z'' == obj.angle + PI/2
        IfcAxis2Placement3D position2 =
                new IfcAxis2Placement3D(new IfcCartesianPoint(
                        obj.getCurveRadius() * cos(obj.getAngle()),
                        obj.getCurveRadius() * sin(obj.getAngle()),
                        0),
                                        new IfcDirection(cos(
                                                obj.getAngle() + PI / 2),
                                                         sin(obj.getAngle() +
                                                                     PI / 2),
                                                         0),
                                        new IfcDirection(0, 0, 1));
        List<IfcAxis2Placement3D> crossSectionPositions =
                Arrays.asList(position1, position2);

        //FIXME: IfcSectionedSpine is not included in the Coordination View
        // MVD, so it's likely that most CAD applications won't be able to
        // render it, this also makes this method not testable at the moment
        IfcSectionedSpine curve = new IfcSectionedSpine(spineCurve,
                                                        crossSections,
                                                        crossSectionPositions);

        IfcShapeRepresentation shapeRepresentation = new IfcShapeRepresentation(
                GEOMETRIC_REPRESENTATION_CONTEXT,
                new IfcLabel("Body"),
                new IfcLabel("SectionedSpine"),
                curve);
        IfcProductDefinitionShape productDefinitionShape =
                new IfcProductDefinitionShape(null, null, shapeRepresentation);
        IfcProxy curveProxy =
                IfcProxy.builder().globalId(new IfcGloballyUniqueId())
                        .ownerHistory(ownerHistory)
                        .name(new IfcLabel(obj.getClass().getSimpleName()))
                        .description(new IfcText(getDescription(obj)))
                        .objectPlacement(new IfcLocalPlacement(null,
                                                               objPosition))
                        .representation(productDefinitionShape)
                        .proxyType(IfcObjectTypeEnum.PRODUCT).build();
        geometries.add(curveProxy);
    }

    /**
     * @param obj The {@link Dielectric} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Dielectric obj) {

    }

    /**
     * @param obj The {@link Dish} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Dish obj) {

    }

    /**
     * @param obj The {@link DualExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull DualExpansionJoint obj) {

    }

    /**
     * @param obj The {@link EccentricCone} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull EccentricCone obj) {

    }

    /**
     * @param obj The {@link Empty} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Empty obj) {
        resolveLocation(obj);
    }

    /**
     * @param obj The {@link Endplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Endplate obj) {

    }

    /**
     * @param obj The {@link ExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull ExpansionJoint obj) {

    }

    /**
     * @param obj The {@link FaceSet} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull FaceSet obj) {

    }

    /**
     * @param obj The {@link FourWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull FourWaysValve obj) {

    }

    /**
     * @param obj The {@link Instrument} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Instrument obj) {

    }

    /**
     * @param obj The {@link Ladder} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Ladder obj) {

    }

    /**
     * @param obj The {@link Mesh} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Mesh obj) {

    }

    /**
     * @param obj The {@link Nozzle} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Nozzle obj) {

    }

    /**
     * @param obj The {@link OrthoValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull OrthoValve obj) {

    }

    /**
     * @param obj The {@link RectangularBlind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull RectangularBlind obj) {

    }

    /**
     * @param obj The {@link RectangularEndplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull RectangularEndplate obj) {

    }

    /**
     * @param obj The {@link RectangularFlange} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull RectangularFlange obj) {

    }

    /**
     * @param obj The {@link RectangularPlate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull RectangularPlate obj) {

    }

    /**
     * @param obj The {@link RectangularShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull RectangularShell obj) {

    }

    /**
     * @param obj The {@link Ring} to convert.
     * @throws NullPointerException If {@code obj} is null.
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
        IfcAxis2Placement3D objPosition = resolveLocation(obj);
        //creating a parallelogram that is the right part of the vertical
        // section of the shell
        double height = obj.getLength();
        double radiusDifference = obj.getRadius1() - obj.getRadius2();
        double side =
                sqrt((height * height) + (radiusDifference * radiusDifference));
        //TODO: convert lining as a separate solid with a different color
        // (green) for all Primitives
        double base = obj.getThickness() + obj.getLining();
        // alfa is the bottom right angle of the parallelogram
        double sinAlfa = height / side;
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
                        minRadius - (base / 2) + (abs(topBaseOffset) / 2),
                        height / 2), new IfcDirection(1, 0));

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
    public void addObject(@NonNull Sphere obj) {

    }

    /**
     * @param obj The {@link Stair} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Stair obj) {

    }

    /**
     * @param obj The {@link Sweep} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Sweep obj) {

    }

    /**
     * @param obj The {@link TankShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull TankShell obj) {

    }

    /**
     * @param obj The {@link Tee} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Tee obj) {

    }

    /**
     * @param obj The {@link ThreeWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull ThreeWaysValve obj) {

    }

    /**
     * @param obj The {@link Valve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     */
    @Override
    public void addObject(@NonNull Valve obj) {

    }
}
