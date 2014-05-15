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

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.resources.image.ImageUtilities;
import org.geotools.util.logging.Logging;

import javax.media.jai.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.RasterFactory;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.TranslateDescriptor;
import java.util.*;
import java.util.List;

/**
 * This class reads decoded tiles from the queue and performs the mosaicing and
 * scaling
 * 
 * @author mcr
 * 
 *
 * @source $URL$
 */
public class ImageComposerThread extends AbstractThread {
	/** Logger. */
	protected final static Logger LOGGER = Logging
			.getLogger(ImageComposerThread.class.getPackage().getName());

	protected GridCoverageFactory coverageFactory;

	private GridCoverage2D gridCoverage2D;

	private Color outputTransparentColor,backgroundColor;

	private boolean xAxisSwitch;

	public ImageComposerThread(Color backgroundColor,Color outputTransparentColor, Rectangle pixelDimension,
			GeneralEnvelope requestEnvelope, ImageLevelInfo levelInfo,
			LinkedBlockingQueue<TileQueueElement> tileQueue, Config config,
			boolean xAxisSwitch, GridCoverageFactory coverageFactory) {
		super(pixelDimension, requestEnvelope, levelInfo, tileQueue, config);
		this.outputTransparentColor = outputTransparentColor;
		this.backgroundColor = backgroundColor;
		this.xAxisSwitch = xAxisSwitch;
		this.coverageFactory = coverageFactory;
	}

	private Dimension getStartDimension() {
		double width;
		double height;

        // TODO nat - creating start image with requested dimensions
        width = pixelDimension.getWidth();
        height = pixelDimension.getHeight();

		return new Dimension((int) Math.round(width), (int) Math.round(height));
	}

	private BufferedImage getStartImage(BufferedImage copyFrom) {
		Dimension dim = getStartDimension();
		Hashtable<String,Object> properties = null;
		
		if (copyFrom.getPropertyNames()!=null) {
		    properties = new Hashtable<String, Object>();
		    for (String name : copyFrom.getPropertyNames()) {
		        properties.put(name, copyFrom.getProperty(name));
		    }
		}

		SampleModel sm = copyFrom.getSampleModel().createCompatibleSampleModel((int) dim.getWidth(), (int) dim.getHeight());

        ColorModel colorModel = copyFrom.getColorModel();
        boolean alphaPremultiplied = copyFrom.isAlphaPremultiplied();

        /*
         Create empty image with correct number of bands - extracted existing code into a method createImage
         */
        return createImage(sm, colorModel, alphaPremultiplied, properties);
	}

    /*
     Create empty image with correct number of bands - extracted existing code into a method createImage
    */
    private BufferedImage createImage(SampleModel sm, ColorModel colorModel, boolean alphaPremultiplied, Hashtable<String, Object> properties) {
        WritableRaster raster = Raster.createWritableRaster(sm, null);

        DataBuffer dataBuffer = createDataBufferFilledWithNoDataValues(raster, colorModel.getPixelSize());
        raster = Raster.createWritableRaster(sm, dataBuffer, null);
        BufferedImage image=  new BufferedImage(colorModel, raster, alphaPremultiplied, properties);
        if (levelInfo.getNoDataValue()==null) {
            Graphics2D g2D = (Graphics2D) image.getGraphics();
            Color save = g2D.getColor();
            g2D.setColor(backgroundColor);
            g2D.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2D.setColor(save);
        }
        return  image;
    }

    private BufferedImage createDefaultImage(int imageType, Dimension dim) {
        if (imageType == BufferedImage.TYPE_CUSTOM)
            imageType = ImageMosaicJDBCReader.DEFAULT_IMAGE_TYPE;

        LOGGER.fine("Creating default empty image for imageType: " + imageType);

        BufferedImage image = new BufferedImage((int) dim.getWidth(), (int) dim
                .getHeight(), imageType);

        Graphics2D g2D = (Graphics2D) image.getGraphics();
        Color save = g2D.getColor();
        g2D.setColor(backgroundColor);
        g2D.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2D.setColor(save);

        return image;
    }

    private BufferedImage getStartImage(int imageType) {
		Dimension dim = getStartDimension();

        /*
         Create empty image with correct number of bands - check is we have values for
         numberOfBands, noDataValue and pixelType. If not - use old way of creating an
         image based on imageType. Otherwise - reuse createImage method which calls
         createDataBufferFilledWithNoDataValues method
        */
        if (levelInfo.getNumberOfBands() <= 0 || levelInfo.getNoDataValue() == null) {
            return createDefaultImage(imageType, dim);
        }
        int dataType = resolveDataType(levelInfo.getPixelType());
        if (dataType == -1) {
            return createDefaultImage(imageType, dim);
        }

        int numOfBands = levelInfo.getNumberOfBands();
        LOGGER.fine("Creating empty image for numberOfBands: " + numOfBands + ", data type: " + dataType + ", noDataValue: " + levelInfo.getNoDataValue());
        SampleModel sm = RasterFactory.createPixelInterleavedSampleModel(dataType, dim.width, dim.height, numOfBands);
        ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false, Transparency.OPAQUE, dataType);

        return createImage(sm, colorModel, false, new Hashtable<String, Object>());
	}

    @Override
	public void run() {
        BufferedImage image = null;

        TileQueueElement queueObject;

        try {
            while ((queueObject = tileQueue.take()).isEndElement() == false) {

                if (image == null) {
                    image = getStartImage(queueObject.getTileImage());
                }

                float posx = (float) Math.floor(new Float(queueObject.getEnvelope().getMinimum(0) - requestEnvelope.getMinimum(0)) / resX);
                float posy = (float) Math.floor(new Float(requestEnvelope.getMaximum(1) - queueObject.getEnvelope().getMaximum(1)) / resY);

                BufferedImage rescaledTileImage = rescaleImageViaPlanarImage(queueObject.getTileImage());

                image.getRaster().setRect((int) posx, (int) posy, rescaledTileImage.getRaster());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (image == null) // no tiles ??
            image = getStartImage(ImageMosaicJDBCReader.DEFAULT_IMAGE_TYPE);

        GeneralEnvelope resultEnvelope = null;

		if (xAxisSwitch) {
			Rectangle2D tmp = new Rectangle2D.Double(requestEnvelope
					.getMinimum(1), requestEnvelope.getMinimum(0),
					requestEnvelope.getSpan(1), requestEnvelope.getSpan(0));
			resultEnvelope = new GeneralEnvelope(tmp);
			resultEnvelope.setCoordinateReferenceSystem(requestEnvelope
					.getCoordinateReferenceSystem());
		} else {
			resultEnvelope = requestEnvelope;
		}

        if (outputTransparentColor == null)
            gridCoverage2D= coverageFactory.create(config.getCoverageName(),
                    image, resultEnvelope);
        else {
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.fine("Support for alpha on final mosaic");
            RenderedImage result =  ImageUtilities.maskColor(outputTransparentColor,image);
            gridCoverage2D = coverageFactory.create(config.getCoverageName(),
                    result, resultEnvelope);
        }
    }

    // TODO nat - since we are not creating a master startImage now, which would act as a background for areas with
    // no db tiles, this method will create a series of empty images/tiles to cover entire result area so tiles
    // we get from db can go over over the top of these
    private List<RenderedOp> createEmptyTilesToCoverResultImage(Dimension resultDimension, RenderedImage copyFrom) {
        int emptyTileSize = 512;
        List<RenderedOp> renderedOps = new ArrayList<RenderedOp>();

        int numOfHorizontalTiles = (int) Math.ceil(resultDimension.getWidth() / emptyTileSize);
        int numOfVerticalTiles = (int) Math.ceil(resultDimension.getHeight() / emptyTileSize);

        double defaultTileWidth = resultDimension.getWidth() / numOfHorizontalTiles;
        double defaultTileHeight = resultDimension.getHeight() / numOfVerticalTiles;

        for (int x = 0; x < numOfHorizontalTiles; x++) {
            for (int y = 0; y < numOfVerticalTiles; y++) {
                SampleModel sm = copyFrom.getSampleModel().createCompatibleSampleModel((int) Math.ceil(defaultTileWidth), (int) Math.ceil(defaultTileHeight));
                BufferedImage emptyTile = createImage(sm, copyFrom.getColorModel(), copyFrom.getColorModel().isAlphaPremultiplied(), new Hashtable<String, Object>());

                double posX = x * defaultTileWidth;
                double posY = y * defaultTileHeight;

                RenderedOp renderedOp = TranslateDescriptor.create(emptyTile, (float) posX, (float) posY, null, null);
                renderedOps.add(renderedOp);
            }
        }
        return renderedOps;
    }

    GridCoverage2D getGridCoverage2D() {
		return gridCoverage2D;
	}

    private DataBuffer createDataBufferFilledWithNoDataValues(WritableRaster raster, int pixelSize) {
        int dataType = raster.getDataBuffer().getDataType();
        
        Number noDataValue = levelInfo.getNoDataValue();

        int dataBufferSize = raster.getDataBuffer().getSize();        
        int nrBanks = raster.getDataBuffer().getNumBanks();
        DataBuffer dataBuffer;
        switch (dataType) {
            case DataBuffer.TYPE_INT:                
                int[][] intDataArray = new int[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(intDataArray[i], noDataValue.intValue());
                }   
                dataBuffer = new DataBufferInt(intDataArray, dataBufferSize);                    
                break;
            case DataBuffer.TYPE_FLOAT:
                float[][] floatDataArray = new float[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(floatDataArray[i], noDataValue.floatValue());
                }   
                dataBuffer = new DataBufferFloat(floatDataArray, dataBufferSize);                    
                break;
            case DataBuffer.TYPE_DOUBLE:
                double[][] doubleDataArray = new double[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(doubleDataArray[i], noDataValue.doubleValue());
                }   
                dataBuffer = new DataBufferDouble(doubleDataArray, dataBufferSize);                    
                break;
                
            case DataBuffer.TYPE_SHORT:
                short[][] shortDataArray = new short[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(shortDataArray[i], noDataValue.shortValue());
                }   
                dataBuffer = new DataBufferShort(shortDataArray, dataBufferSize);                    
                break;
                
            case DataBuffer.TYPE_BYTE:
                byte[][] byteDataArray = new byte[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(byteDataArray[i], noDataValue.byteValue());
                }   
                dataBuffer = new DataBufferByte(byteDataArray, dataBufferSize);                    
                break;                

            case DataBuffer.TYPE_USHORT:
                short[][] ushortDataArray = new short[nrBanks][dataBufferSize];
                if (noDataValue!=null) {
                    for (int i = 0; i < nrBanks;i++)
                        Arrays.fill(ushortDataArray[i], noDataValue.shortValue());
                }   
                dataBuffer = new DataBufferUShort(ushortDataArray, dataBufferSize);                    
                break;                                
                
            default:
                throw new IllegalStateException("Couldn't create DataBuffer for  data type " + dataType
                        + " and " + pixelSize + " pixel size");
        }
        return dataBuffer;
    }

    /*
     Create empty image with correct number of bands - resolve dataType based on pixelType.
     Covered here pixelTypes listed at http://postgis.net/docs/RT_ST_BandPixelType.html
     with an exception of '1BB'.
     If received can't resolve dataType - then return '-1' so calling code will create
      a default image (with 3 band raster)
    */
    private int resolveDataType(String pixelType) {
        if (pixelType == null || pixelType.length() <= 0) {
            return -1;
        }

        if ("2BUI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_BYTE;
        }
        if ("4BUI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_BYTE;
        }
        if ("8BSI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_SHORT;
        }
        if ("8BUI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_USHORT;
        }
        if ("16BSI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_SHORT;
        }
        if ("16BUI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_USHORT;
        }
        if ("32BSI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_INT;
        }
        if ("32BUI".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_INT;
        }
        if ("32BF".equalsIgnoreCase(pixelType)) {
            return DataBuffer.TYPE_FLOAT;
        }
        return -1;
    }
}
