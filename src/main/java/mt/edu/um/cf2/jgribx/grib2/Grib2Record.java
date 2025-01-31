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

import mt.edu.um.cf2.jgribx.GribInputStream;
import mt.edu.um.cf2.jgribx.GribRecord;
import mt.edu.um.cf2.jgribx.GribRecordIS;
import mt.edu.um.cf2.jgribx.Logger;
import mt.edu.um.cf2.jgribx.NoValidGribException;
import mt.edu.um.cf2.jgribx.NotSupportedException;
import mt.edu.um.cf2.jgribx.grib2.Grib2RecordGDS.ScanMode;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author AVLAB-USER3
 */
public class Grib2Record extends GribRecord
{
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected GribRecordIS is;
    protected Grib2RecordIDS ids;
    protected List<Grib2RecordLUS> lusList = new ArrayList<>();
    protected List<Grib2RecordGDS> gdsList = new ArrayList<>();
    protected List<Grib2RecordPDS> pdsList = new ArrayList<>();
    protected List<Grib2RecordDRS> drsList = new ArrayList<>();
    protected List<Grib2RecordBMS> bmsList = new ArrayList<>();
    protected List<Grib2RecordDS> dsList = new ArrayList<>();

    public static final double DEFAULT_UNKNOWN_VALUE = 0.0d;

    public static Grib2Record readFromStream(GribInputStream in, GribRecordIS is) throws IOException, NotSupportedException, NoValidGribException
    {
        Grib2Record record = new Grib2Record();
        long recordLength = is.getRecordLength() - is.getLength();

        Grib2RecordDRS drs = null;
        Grib2RecordGDS gds = null;
        Grib2RecordBMS bms = null;
        while (recordLength > 4)
        {
            int section;

            in.mark(10);
            int sectionLength = in.readUINT(4);
            if (sectionLength > recordLength)
            {
                Logger.println("Section appears to be larger than the remaining length in the record.", Logger.ERROR);
            }

            section = in.readUINT(1);
            in.reset();
            in.resetBitCounter();

            switch (section)
            {
                case 1:
                    record.ids = Grib2RecordIDS.readFromStream(in);
                    break;
                case 2:
                    in.skip(sectionLength);
                    break;
                case 3:
                    gds = Grib2RecordGDS.readFromStream(in);
                    record.gdsList.add(gds);
                    break;
                case 4:
                    record.pdsList.add(new Grib2RecordPDS(in, is.getDiscipline(), record.ids.referenceTime));
                    break;
                case 5:
                    drs = Grib2RecordDRS.readFromStream(in);
                    record.drsList.add(drs);
                    break;
                case 6:
                    bms = Grib2RecordBMS.readFromStream(in);
                    record.bmsList.add(bms);
                    break;
                case 7:
                    record.dsList.add(Grib2RecordDS.readFromStream(in, drs, gds, bms));
                    break;
                default:
                    throw new NoValidGribException("Invalid section encountered");
            }
            if (in.getByteCounter() != sectionLength)
            {
                Logger.println("Indicated length (" + sectionLength + ") of Section " + section +
                        " does not match actual amount of bytes read (" + in.getByteCounter() + ")", Logger.ERROR);
            }

            recordLength -= sectionLength;
        }

        return record;
    }

    @Override
    public int getCentreId()
    {
        return ids.getCentreId();
    }

    @Override
    public Calendar getForecastTime()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }

        return pdsList.get(0).getForecastTime();
    }

    @Override
    public String getLevelCode()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }

        return pdsList.get(0).getLevelCode();
    }

    @Override
    public String getLevelDescription()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }

        return pdsList.get(0).getLevelDescription();
    }

    @Override
    public String getLevelIdentifier()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }
        return pdsList.get(0).getLevelIdentifier();
    }

    @Override
    public float[] getLevelValues()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }
        return pdsList.get(0).getLayer().getValues();
    }

    @Override
    public String getParameterCode()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }
        return pdsList.get(0).getParameterAbbrev();
    }

    @Override
    public String getParameterDescription()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }
        return pdsList.get(0).getParameterDescription();
    }

    @Override
    public int getProcessId()
    {
        if (pdsList.size() > 1)
        {
            Logger.println("Record contains multiple PDS's", Logger.WARNING);
        }
        return pdsList.get(0).getProcessId();
    }

    @Override
    public Calendar getReferenceTime()
    {
        return ids.referenceTime;
    }

    @Override
    public double getValue(double latitude, double longitude)
    {
        double value;

        if (gdsList.size() > 1)
        {
            Logger.println("Record contains multiple GDS instances", Logger.WARNING);
        }

        Grib2RecordGDS gds = gdsList.get(0);

        if (latitude < gds.lat1 || latitude > gds.lat2)
        {
            logger.warn("Latitude was out of scope for the GRIB2 file: {}, {}", gds.lat1, gds.lat2);
            return DEFAULT_UNKNOWN_VALUE;
        }

        if (longitude < gds.lon1 || longitude > gds.lon2)
        {
            logger.warn("Longitude was out of scope for the GRIB2 file: {}, {}", gds.lon1, gds.lon2);
            return DEFAULT_UNKNOWN_VALUE;
        }

        int j = (int) Math.round((latitude - gds.getGridLatStart()) / gds.getGridDeltaY());     // j = index_closest_latitude
        int i = (int) Math.round((longitude - gds.getGridLonStart()) / gds.getGridDeltaX());    // i = index_closest_longitude

        ScanMode scanMode = gds.scanMode;

        if (scanMode.iDirectionEvenRowsOffset || scanMode.iDirectionOddRowsOffset || scanMode.jDirectionOffset ||
                !scanMode.rowsNiNjPoints || scanMode.rowsZigzag)
        {
            System.err.println("Unsupported scan mode found");
        }

        if (scanMode.iDirectionConsecutive)
        {
            value = dsList.get(0).data[gds.gridNi * j + i];
        } else
        {
            value = dsList.get(0).data[gds.gridNj * i + j];
        }

        return value;
    }

    public static double getValueFromParsedObject(Pair<Grib2RecordGDS, Pair<String, float[]>> values, double latitude, double longitude)
    {
        double value;
        Grib2RecordGDS gds = values.getKey();
        float[] windValues = values.getValue().getValue();

        if (latitude < gds.lat1 || latitude > gds.lat2)
        {
            logger.warn("Latitude was out of scope for the GRIB2 file: {}, {}", gds.lat1, gds.lat2);
            return DEFAULT_UNKNOWN_VALUE;
        }

        if (longitude < gds.lon1 || longitude > gds.lon2)
        {
            logger.warn("Longitude was out of scope for the GRIB2 file: {}, {}", gds.lon1, gds.lon2);
            return DEFAULT_UNKNOWN_VALUE;
        }

        int j = (int) Math.round((latitude - gds.getGridLatStart()) / gds.getGridDeltaY());     // j = index_closest_latitude
        int i = (int) Math.round((longitude - gds.getGridLonStart()) / gds.getGridDeltaX());    // i = index_closest_longitude

        ScanMode scanMode = gds.scanMode;

        if (scanMode.iDirectionEvenRowsOffset || scanMode.iDirectionOddRowsOffset || scanMode.jDirectionOffset ||
                !scanMode.rowsNiNjPoints || scanMode.rowsZigzag)
        {
            System.err.println("Unsupported scan mode found");
        }

        if (scanMode.iDirectionConsecutive)
        {
            value = windValues[gds.gridNi * j + i];
        } else
        {
            value = windValues[gds.gridNj * i + j];
        }

        return value;
    }

    @Override
    public float[] getValues()
    {
        if (dsList.size() > 1)
        {
            logger.warn("Unsupported Grib2RecordDS count, maximum allowed is 1 (currently the only implementation).");
            return null;
        }

        return dsList.get(0).data;
    }

    /**
     * Access to grid definition section (GDS) records.
     *
     * @return GDS records
     */
    public List<Grib2RecordGDS> getGDS()
    {
        return gdsList;
    }

    /**
     * Access to data section (DS) records.
     *
     * @return DS records
     */
    public List<Grib2RecordDS> getDS()
    {
        return dsList;
    }
}
