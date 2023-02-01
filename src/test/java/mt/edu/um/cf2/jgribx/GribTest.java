package mt.edu.um.cf2.jgribx;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GribTest
{
    @BeforeClass
    public static void setUpBeforeClass()
    {
        // Prepare format for reference times
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss 'UTC'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Configure logging
        Logger.setLoggingMode(Logger.LoggingMode.CONSOLE);
        JGribX.setLoggingLevel(Logger.DEBUG);
    }

    @Test
    public void testVersion()
    {
        // Check that version string format conforms to SemVer (taken from: https://semver.org/)
        Pattern pattern = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
        String version = JGribX.getVersion();
        Matcher match = pattern.matcher(version);
        assertTrue("Version string format is valid: " + version, match.find());
    }

        @Test
    public void testGrib1Gfs3() throws NotSupportedException, IOException, NoValidGribException
    {
        final String FILENAME = "/GRIB2-Example.grb2";

        // Define expected data
        final int N_RECORDS_EXPECTED = 2795;
        final int GRIB_EDITION = 2;
        final int[] WEATHER_CENTRES = {7};
        final int[] GENERATING_PROCESSES = {81, 96};

        URL url = GribTest.class.getResource(FILENAME);
        System.out.println("Path to file: " + url);
        GribFile gribFile = new GribFile(url.openStream());

        assertEquals("Records read successfully", N_RECORDS_EXPECTED, gribFile.getRecordCount());
        assertEquals("GRIB edition", GRIB_EDITION, gribFile.getEdition());
        assertArrayEquals("Weather centres", WEATHER_CENTRES, gribFile.getCentreIDs());
        assertArrayEquals("Generating processes", GENERATING_PROCESSES, gribFile.getProcessIDs());
    }

    @Test
    public void testGrib2QuasiRegularGrid() throws IOException, NoValidGribException, NotSupportedException
    {
        final String FILENAME = "/GRIB2-Example.grb2";

        // Define expected data
        final int EDITION = 2;
        final int[] WEATHER_CENTRES = {7};

        URL url = GribTest.class.getResource(FILENAME);
        GribFile file = new GribFile(url.openStream());

        assertEquals("GRIB edition", EDITION, file.getEdition());
        assertArrayEquals("Weather centres", WEATHER_CENTRES, file.getCentreIDs());
    }

    private static float getMaxValue(float[] values)
    {
        float max = values[0];
        for (int i = 1; i < values.length; i++)
        {
            if (values[i] > max)
            {
                max = values[i];
            }
        }
        return max;
    }
}
