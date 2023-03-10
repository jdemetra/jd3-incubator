/*
 * Copyright 2022 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package demetra.highfreq.r;

import demetra.data.DoubleSeq;
import tck.demetra.data.MatrixSerializer;
import demetra.math.functions.Optimizer;
import demetra.math.matrices.Matrix;
import demetra.ssf.SsfInitialization;
import demetra.timeseries.TsDomain;
import demetra.timeseries.TsPeriod;
import demetra.timeseries.calendars.EasterRelatedDay;
import demetra.timeseries.calendars.FixedDay;
import demetra.timeseries.calendars.Holiday;
import demetra.timeseries.calendars.HolidaysOption;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jdplus.highfreq.extendedairline.ExtendedAirlineMapping;
import jdplus.math.matrices.FastMatrix;
import jdplus.msts.AtomicModels;
import jdplus.msts.CompositeModel;
import jdplus.msts.CompositeModelEstimation;
import jdplus.msts.ModelEquation;
import jdplus.msts.StateItem;
import jdplus.ssf.ISsfLoading;
import jdplus.ssf.StateStorage;
import jdplus.timeseries.calendars.HolidaysUtility;

/**
 *
 * @author palatej
 */
public class SplinesTest {

    final static DoubleSeq EDF;

    static {
        DoubleSeq y;
        try {
            InputStream stream = ExtendedAirlineMapping.class.getResourceAsStream("edf.txt");
            Matrix edf = MatrixSerializer.read(stream);
            y = edf.column(0);
        } catch (IOException ex) {
            y = null;
        }
        EDF = y;
    }

    private static void addDefault(List<Holiday> holidays) {
        holidays.add(FixedDay.NEWYEAR);
        holidays.add(FixedDay.MAYDAY);
        holidays.add(FixedDay.ASSUMPTION);
        holidays.add(FixedDay.ALLSAINTSDAY);
        holidays.add(FixedDay.CHRISTMAS);
        holidays.add(EasterRelatedDay.EASTERMONDAY);
        holidays.add(EasterRelatedDay.ASCENSION);
        holidays.add(EasterRelatedDay.WHITMONDAY);
    }

    public static Holiday[] france() {
        List<Holiday> holidays = new ArrayList<>();
        addDefault(holidays);
        holidays.add(new FixedDay(5, 8));
        holidays.add(new FixedDay(7, 14));
        holidays.add(FixedDay.ARMISTICE);
        return holidays.stream().toArray(i -> new Holiday[i]);
    }
    public static void main(String[] args) {
        DoubleSeq y = EDF.log();
        
        TsPeriod start = TsPeriod.daily(1996, 1, 1);
        FastMatrix X = HolidaysUtility.regressionVariables(france(), TsDomain.of(start, y.length()), HolidaysOption.Skip, new int[]{6,7}, false);
        

        long t0 = System.currentTimeMillis();

        int[] pos = new int[]{0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 350, 358, 365};

        CompositeModel model = new CompositeModel();
        StateItem l = AtomicModels.localLinearTrend("l", .01, .01, false, false);
        StateItem sw = AtomicModels.seasonalComponent("sw", "Crude", 7, .01, false);
        StateItem sy = AtomicModels.regularSplineComponent("sy", pos, 0, .01, false);
        StateItem reg=AtomicModels.timeVaryingRegression("reg", X, 0.01, false);
        StateItem n = AtomicModels.noise("n", .01, false);
        ModelEquation eq = new ModelEquation("eq1", 0, true);
        eq.add(l);
        eq.add(sw);
        eq.add(sy);
        eq.add(n);
        eq.add(reg);
        model.add(l);
        model.add(sw);
        model.add(sy);
        model.add(n);
        model.add(reg);
        int len = y.length();
        FastMatrix M = FastMatrix.make(len, 1);
        model.add(eq);
        M.column(0).copy(y);
        CompositeModelEstimation mrslt = model.estimate(M, false, true, SsfInitialization.Augmented_Robust, Optimizer.LevenbergMarquardt, 1e-5, null);
        long t1 = System.currentTimeMillis();
        System.out.println(t1 - t0);
        StateStorage smoothedStates = mrslt.getSmoothedStates();
        ISsfLoading loading = sy.defaultLoading(0);
        int[] cmpPos = mrslt.getCmpPos();
        int[] cmpDim = mrslt.getSsf().componentsDimension();
        System.out.println(smoothedStates.getComponent(cmpPos[0]));
        System.out.println(smoothedStates.getComponent(cmpPos[1]));
        System.out.println(smoothedStates.getComponent(cmpPos[3]));
        for (int i = 0; i < smoothedStates.size(); ++i) {
            double z = loading.ZX(i, smoothedStates.a(i).extract(cmpPos[2], cmpDim[2]));
            System.out.print(z);
            System.out.print('\t');
        }
        System.out.println();
        for (int i = 0; i < smoothedStates.size(); ++i) {
            double z = X.row(i).dot(smoothedStates.a(i).extract(cmpPos[4], cmpDim[4]));
            System.out.print(z);
            System.out.print('\t');
        }
    }

}
