/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.gce.imagemosaic.jdbc;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.concurrent.LinkedBlockingQueue;

import javax.media.jai.Interpolation;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.TiledImage;
import javax.media.jai.operator.ScaleDescriptor;

import org.geotools.geometry.GeneralEnvelope;

/**
 * this class is the base class for concrete thread classes
 * 
 * @author mcr
 * 
 */
abstract class AbstractThread extends Thread {
    LinkedBlockingQueue<TileQueueElement> tileQueue;

	Config config;

	GeneralEnvelope requestEnvelope;

	Rectangle pixelDimension;

	ImageLevelInfo levelInfo;

	float rescaleX;

	float rescaleY;

	float resX;

	float resY;

	/**
	 * Constructor
	 * 
	 * @param pixelDimenison
	 *            the requested pixel dimension
	 * @param requestEnvelope
	 *            the requested world rectangle
	 * @param levelInfo
	 *            levelinfo of selected pyramid
	 * @param tileQueue
	 *            queue for thread synchronization
	 * @param config
	 *            the configuraton of the plugin
	 * 
	 */
	AbstractThread(Rectangle pixelDimenison, GeneralEnvelope requestEnvelope,
			ImageLevelInfo levelInfo,
			LinkedBlockingQueue<TileQueueElement> tileQueue, Config config) {
		super();
		this.config = config;
		this.tileQueue = tileQueue;
		this.requestEnvelope = requestEnvelope;
		this.levelInfo = levelInfo;
		this.pixelDimension = pixelDimenison;

        resX = new Float(requestEnvelope.getSpan(0)).floatValue() / new Float(pixelDimenison.getWidth()).floatValue();
        resY = new Float(requestEnvelope.getSpan(1)).floatValue() / new Float(pixelDimenison.getHeight()).floatValue();
		rescaleX = new Float(levelInfo.getResX()).floatValue() / resX;
		rescaleY = new Float(levelInfo.getResY()).floatValue() / resY;
    }


    protected BufferedImage rescaleImageViaPlanarImage(BufferedImage image) {
        float rescaleX1;
        float rescaleY1;

        double calcX = image.getWidth() * rescaleX;
        double targetPixelX = Math.ceil(calcX);
        rescaleX1 = new Float(targetPixelX / image.getWidth());

        double calcY = image.getHeight() * rescaleY;
        double targetPixelY = Math.ceil(calcY);
        rescaleY1 = new Float(targetPixelY / image.getHeight());

        PlanarImage planarImage = new TiledImage(image, image.getWidth(), image.getHeight());

        int interpolation= Interpolation.INTERP_NEAREST;

        if (config.getInterpolation().intValue() == 2)
            interpolation = Interpolation.INTERP_BILINEAR;

        if (config.getInterpolation().intValue() == 3)
            interpolation = Interpolation.INTERP_BICUBIC;

        RenderedOp result = ScaleDescriptor.create(planarImage, rescaleX1, rescaleY1, 0.0f, 0.0f, Interpolation.getInstance(interpolation), null);
        WritableRaster scaledImageRaster = (WritableRaster) result.getData();

        ColorModel colorModel = image.getColorModel();

        BufferedImage scaledImage = new BufferedImage(colorModel, scaledImageRaster, image.isAlphaPremultiplied(), null);
        return scaledImage;
    }

}
