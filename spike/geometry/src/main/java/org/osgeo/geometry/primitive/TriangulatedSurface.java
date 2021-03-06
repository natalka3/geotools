/*
 *    OSGeom -- Geometry Collab
 *
 *    (C) 2009, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2001-2009 Department of Geography, University of Bonn
 *    (C) 2001-2009 lat/lon GmbH
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
package org.osgeo.geometry.primitive;

import java.util.List;

import org.osgeo.geometry.primitive.patches.Triangle;

/**
 * A {@link Surface} that consists only of {@link Triangle} patches.
 *
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 *
 * @version. $Revision$, $Date$
 */
public interface TriangulatedSurface extends Surface {

    /**
     * Must always return {@link Surface.SurfaceType#TriangulatedSurface}.
     *
     * @return {@link Surface.SurfaceType#TriangulatedSurface}
     */
    public SurfaceType getSurfaceType();

    /**
     * Returns the patches that constitute this surface.
     *
     * @return the patches that constitute this surface
     */
    public List<Triangle> getPatches();
}
