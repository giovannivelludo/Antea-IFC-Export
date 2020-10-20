/*
 * This file is part of Antea IFC Export.
 *
 * Author: Giovanni Velludo
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2020 Giovanni Velludo
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

import java.util.Map;

/**
 * Interface to be implemented by builders to be used by {@link EywaReader}.
 */
public interface EywaConverter {
    /**
     * @return The result of the conversion.
     *
     * @throws ConversionException If an error occurs during conversion.
     */
    Object getResult();

    /**
     * Call this method to use EywaRoot's hints in the conversion (if
     * possible).
     *
     * @param hints {@link EywaRoot#getHints()}
     * @throws ConversionException If an error occurs during conversion.
     */
    void addHints(Map<String, Object> hints);

    /**
     * @param obj The {@link Beam} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Beam obj);

    /**
     * @param obj The {@link Blind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Blind obj);

    /**
     * @param obj The {@link Box} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Box obj);

    /**
     * @param obj The {@link Collar} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Collar obj);

    /**
     * @param obj The {@link Curve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Curve obj);

    /**
     * @param obj The {@link Dielectric} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Dielectric obj);

    /**
     * @param obj The {@link Dish} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Dish obj);

    /**
     * @param obj The {@link DualExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(DualExpansionJoint obj);

    /**
     * @param obj The {@link EccentricCone} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(EccentricCone obj);

    /**
     * @param obj The {@link Empty} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Empty obj);

    /**
     * @param obj The {@link Endplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Endplate obj);

    /**
     * @param obj The {@link ExpansionJoint} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(ExpansionJoint obj);

    /**
     * @param obj The {@link FaceSet} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(FaceSet obj);

    /**
     * @param obj The {@link FourWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(FourWaysValve obj);

    /**
     * @param obj The {@link Instrument} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Instrument obj);

    /**
     * @param obj The {@link Ladder} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Ladder obj);

    /**
     * @param obj The {@link Mesh} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Mesh obj);

    /**
     * @param obj The {@link Nozzle} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Nozzle obj);

    /**
     * @param obj The {@link OrthoValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(OrthoValve obj);

    /**
     * @param obj The {@link RectangularBlind} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(RectangularBlind obj);

    /**
     * @param obj The {@link RectangularEndplate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(RectangularEndplate obj);

    /**
     * @param obj The {@link RectangularFlange} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(RectangularFlange obj);

    /**
     * @param obj The {@link RectangularPlate} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(RectangularPlate obj);

    /**
     * @param obj The {@link RectangularShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(RectangularShell obj);

    /**
     * @param obj The {@link Ring} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Ring obj);

    /**
     * @param obj The {@link Shell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Shell obj);

    /**
     * @param obj The {@link Sphere} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Sphere obj);

    /**
     * @param obj The {@link Stair} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Stair obj);

    /**
     * @param obj The {@link Sweep} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Sweep obj);

    /**
     * @param obj The {@link TankShell} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(TankShell obj);

    /**
     * @param obj The {@link Tee} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Tee obj);

    /**
     * @param obj The {@link ThreeWaysValve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(ThreeWaysValve obj);

    /**
     * @param obj The {@link Valve} to convert.
     * @throws NullPointerException If {@code obj} is null.
     * @throws ConversionException  If an error occurs during conversion.
     */
    void addObject(Valve obj);
}
