/*
 * ============================================================================
 * JGribX
 * ============================================================================
 * Written by Andrew Spiteri <andrew.spiteri@um.edu.mt>
 * Adapted from JGRIB: http://jgrib.sourceforge.net/
 *
 * Licensed under MIT: https://github.com/spidru/JGribX/blob/master/LICENSE
 * ============================================================================
 */
package mt.edu.um.cf2.jgribx.grib2;

import mt.edu.um.cf2.jgribx.Bytes2Number;
import mt.edu.um.cf2.jgribx.GribInputStream;
import mt.edu.um.cf2.jgribx.NotSupportedException;

import java.io.IOException;

/**
 * @author spidru
 */
public class Grib2RecordGDSLatLon extends Grib2RecordGDS
{
    public Grib2RecordGDSLatLon(GribInputStream in) throws IOException, NotSupportedException
    {
        super(in);
        earthShape = in.readUINT(1);
        int radiusScaleFactor = in.readUINT(1);
        int radiusScaledValue = in.readUINT(4);
        int majorScaleFactor = in.readUINT(1);
        int majorScaledValue = in.readUINT(4);
        int minorScaleFactor = in.readUINT(1);
        int minorScaledValue = in.readUINT(4);
        gridNi = in.readUINT(4);
        gridNj = in.readUINT(4);

        if (gridNj == -1)
        {
            throw new NotSupportedException("Quasi-regular grids with variable Nj is not yet supported");
        }

        int basicAngle = in.readUINT(4);
        int basicAngleSubdiv = in.readUINT(4);
        if (basicAngle == 0)
        {
            lat1 = in.readINT(4, Bytes2Number.INT_SM) / 1.0e6;
            lon1 = in.readINT(4, Bytes2Number.INT_SM) / 1.0e6;
        }
        int flags = in.readUINT(1);
        boolean iDirectionIncrementsGiven = (flags & 0x20) == 0x20;
        boolean jDirectionIncrementsGiven = (flags & 0x10) == 0x10;
        if (basicAngle == 0)
        {
            lat2 = in.readINT(4, Bytes2Number.INT_SM) / 1.0e6;
            lon2 = in.readINT(4, Bytes2Number.INT_SM) / 1.0e6;
        }
        /* [64-67] i-Direction Increment Di */
        if (iDirectionIncrementsGiven)
        {
            if (basicAngle == 0)
            {
                gridDi = in.readUINT(4) / 1.0e6;
            }
        }
        /* [68-71] j-Direction Increment Dj */
        if (jDirectionIncrementsGiven)
        {
            if (basicAngle == 0)
            {
                gridDj = in.readUINT(4) / 1.0e6;
            }
        }
        /* [72] Scanning Mode */
        scanMode = new ScanMode((byte) in.readUINT(1));
        if (!scanMode.iDirectionPositive) gridDi *= -1;
        if (!scanMode.jDirectionPositive) gridDj *= -1;

        if (scanMode.iDirectionEvenRowsOffset || scanMode.iDirectionOddRowsOffset || scanMode.jDirectionOffset || !scanMode.rowsNiNjPoints || scanMode.rowsZigzag)
        {
            throw new NotSupportedException("Unsupported scan mode found");
        }

        if (gridNi == -1 || gridNj == -1)
        {
            quasiRegularGridPoints = new int[this.length - 72];
            for (int i = 0; i < quasiRegularGridPoints.length; i++)
            {
                quasiRegularGridPoints[i] = in.readUINT(1);
            }
        }
    }

    @Override
    protected double[][] getGridCoords()
    {
        if (gridNi == -1 || gridNj == -1)
        {
            return this.getQuasiRegularGridCoords();
        }
        double[][] coords = new double[gridNi * gridNj][2];

        int k = 0;
        for (int j = 0; j < gridNj; j++)
        {
            for (int i = 0; i < gridNi; i++)
            {
                double lon = lon1 + i * gridDi;
                double lat = lat1 + j * gridDj;

                // move x-coordinates to the range -180..180
                if (lon >= 180.0) lon = lon - 360.0;
                if (lon < -180.0) lon = lon + 360.0;
                if (lat > 90.0 || lat < -90.0)
                {
                    System.err.println("GribGDSLatLon.getGridCoords: latitude out of range (-90 to 90).");
                }

                coords[k][0] = lon;
                coords[k][1] = lat;
                k++;
            }
        }
        return coords;
    }

    private double[][] getQuasiRegularGridCoords()
    {
        double[][] coords = new double[nDataPoints][2];

        // Assuming gridNj is fixed and gridNi is variable
        int k = 0;
        for (int j = 0; j < gridNj; j++)
        {
            int gridNi = quasiRegularGridPoints[j];
            for (int i = 0; i < gridNi; i++)
            {
                double gridDi = (lat2 - lat1 + 1) / gridNi;

                double lon = lon1 + i * gridDi;
                double lat = lat1 + j * gridDj;

                coords[k][0] = normalizeAngle(lon);
                coords[k][1] = normalizeAngle(lat);
                k++;
            }
        }
        return coords;
    }

    /**
     * Normalizes the specified angle to fit into the range ]-180, 180]
     *
     * @param angle The angle to normalize (units: degrees)
     * @return The normalized angle (units: degrees)
     * Adapted from: https://stackoverflow.com/questions/2320986/easy-way-to-keeping-angles-between-179-and-180-degrees
     */
    private static double normalizeAngle(double angle)
    {
        // Reduce the angle
        angle %= 360;

        // Force it to become the positive remainder, so that it fits in the range [0, 360]
        angle = (angle + 360) % 360;

        // Force into the minimum absolute value residue class, so that -180 < angle <= 180
        if (angle > 180)
        {
            angle -= 360;
        }

        return angle;
    }

    @Override
    protected double[] getGridXCoords()
    {
        double[] coords = new double[gridNi];
        int k = 0;
        boolean convertTo180 = true;

        for (int x = 0; x < gridNi; x++)
        {
            double lon = lon1 + x * gridDi;

            if (convertTo180)
            {
                // move x-coordinates to the range -180..180
                if (lon >= 180.0) lon = lon - 360.0;
                if (lon < -180.0) lon = lon + 360.0;
            } else
            { // handle wrapping at 360
                if (lon >= 360.0) lon = lon - 360.0;
            }
            coords[k++] = lon;
        }
        return coords;
    }

    @Override
    protected double[] getGridYCoords()
    {
        double[] coords = new double[gridNj];
        int k = 0;

        for (int y = 0; y < gridNj; y++)
        {
            double lat = lat1 + y * gridDj;
            if (lat > 90.0 || lat < -90.0)
            {
                System.err.println("GribGDSLatLon.getYCoords: latitude out of range (-90 to 90).");
            }
            coords[k++] = lat;
        }
        return coords;
    }

    @Override
    protected double getGridDeltaX()
    {
        return gridDi;
    }

    @Override
    protected double getGridDeltaY()
    {
        return gridDj;
    }

    @Override
    protected double getGridLatStart()
    {
        return lat1;
    }

    @Override
    protected double getGridLonStart()
    {
        return lon1;
    }

    @Override
    protected int getGridSizeX()
    {
        return gridNi;
    }

    @Override
    protected int getGridSizeY()
    {
        return gridNj;
    }
}
