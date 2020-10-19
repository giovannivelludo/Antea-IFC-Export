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

import it.imc.persistence.po.eytukan.*;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Class used to convert the 3D model contained in an {@link EywaRoot} to
 * another format, depending on the {@link EywaConverter} passed in the
 * constructor.<br> To convert an {@link EywaRoot}: pass an {@link
 * EywaConverter} in the constructor, call {@link #convert(EywaRoot)}, then
 * {@link #getResult()}.<br>If the {@code builder} passed to the constructor can
 * be used for multiple conversions, then the instance of this class can be
 * reused.
 */
public class EywaReader {
    private EywaConverter builder;
    private final Map<Class<?>, Consumer<Primitive>> builderMethod = Collections
            .unmodifiableMap(new HashMap<Class<?>, Consumer<Primitive>>() {{
                put(Beam.class, obj -> builder.addObject((Beam) obj));
                put(Blind.class, obj -> builder.addObject((Blind) obj));
                put(Box.class, obj -> builder.addObject((Box) obj));
                put(Collar.class, obj -> builder.addObject((Collar) obj));
                put(Curve.class, obj -> builder.addObject((Curve) obj));
                put(Dielectric.class, obj -> builder.addObject((Dielectric) obj));
                put(Dish.class, obj -> builder.addObject((Dish) obj));
                put(DualExpansionJoint.class, obj -> builder.addObject((DualExpansionJoint) obj));
                put(EccentricCone.class, obj -> builder.addObject((EccentricCone) obj));
                put(Empty.class, obj -> builder.addObject((Empty) obj));
                put(Endplate.class, obj -> builder.addObject((Endplate) obj));
                put(ExpansionJoint.class, obj -> builder.addObject((ExpansionJoint) obj));
                put(FaceSet.class, obj -> builder.addObject((FaceSet) obj));
                put(FourWaysValve.class, obj -> builder.addObject((FourWaysValve) obj));
                put(Instrument.class, obj -> builder.addObject((Instrument) obj));
                put(Ladder.class, obj -> builder.addObject((Ladder) obj));
                put(Mesh.class, obj -> builder.addObject((Mesh) obj));
                put(Nozzle.class, obj -> builder.addObject((Nozzle) obj));
                put(OrthoValve.class, obj -> builder.addObject((OrthoValve) obj));
                put(RectangularBlind.class, obj -> builder.addObject((RectangularBlind) obj));
                put(RectangularEndplate.class, obj -> builder.addObject((RectangularEndplate) obj));
                put(RectangularFlange.class, obj -> builder.addObject((RectangularFlange) obj));
                put(RectangularPlate.class, obj -> builder.addObject((RectangularPlate) obj));
                put(RectangularShell.class, obj -> builder.addObject((RectangularShell) obj));
                put(Ring.class, obj -> builder.addObject((Ring) obj));
                put(Shell.class, obj -> builder.addObject((Shell) obj));
                put(Sphere.class, obj -> builder.addObject((Sphere) obj));
                put(Stair.class, obj -> builder.addObject((Stair) obj));
                put(Sweep.class, obj -> builder.addObject((Sweep) obj));
                put(TankShell.class, obj -> builder.addObject((TankShell) obj));
                put(Tee.class, obj -> builder.addObject((Tee) obj));
                put(ThreeWaysValve.class, obj -> builder.addObject((ThreeWaysValve) obj));
                put(Valve.class, obj -> builder.addObject((Valve) obj));
            }});

    /**
     * If {@code builder} can be used for multiple conversions, then the
     * generated instance of this class can be reused too.
     * @param builder The {@link EywaConverter} to use for the conversion.
     * @throws NullPointerException If {@code builder} is {@code null}.
     */
    public EywaReader(@NonNull EywaConverter builder) {
        this.builder = builder;
    }

    /**
     * Uses the {@link EywaConverter} provided in the constructor to convert the
     * given {@code eywaRoot}.
     * @param eywaRoot The {@link EywaRoot} to convert.
     * @throws NullPointerException If {@code eywaRoot} is {@code null}.
     * @throws IllegalArgumentException If {@code eywaRoot.metadata.use} exists
     * and is not {@code view}, if {@code
     * eywaRoot.object} is {@code null}.
     * @throws ConversionException If an error occurs during conversion.
     */
    public void convert(@NonNull EywaRoot eywaRoot) {
        if (eywaRoot.getMetadata() != null &&
                eywaRoot.getMetadata().getUse() != null &&
                eywaRoot.getMetadata().getUse() != EywaRoot.Use.view) {
            throw new IllegalArgumentException(
                    "eywaRoot.metadata.use must be \"view\", this class can't" +
                            " convert Eywa files using endpoints");
        }
        builder.addHints(eywaRoot.getHints());
        Primitive rootObject = eywaRoot.getObject();
        if (rootObject == null) {
            throw new IllegalArgumentException("can't convert an EywaRoot with no object");
        }
        builderMethod.get(rootObject.getClass()).accept(rootObject);
        rootObject.prePersist();
        addChildren(rootObject);
    }

    /**
     * @param obj The {@link Primitive} for which to pass all descendants to the
     * {@code builder} provided in the constructor.
     * @throws NullPointerException If {@code obj} is {@code null}.
     * @throws ConversionException If an error occurs during conversion.
     */
    private void addChildren(@NonNull Primitive obj) {
        obj.getChildren().forEach(child -> {
            builderMethod.get(child.getClass()).accept(child);
            child.prePersist();
            addChildren(child);
        });
    }

    /**
     * @return The result of the conversion.
     * @throws ConversionException If an error occurs during conversion.
     */
    public Object getResult() {
        return builder.getResult();
    }
}
