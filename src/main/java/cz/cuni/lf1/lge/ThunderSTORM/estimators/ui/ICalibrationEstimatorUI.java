package cz.cuni.lf1.lge.ThunderSTORM.estimators.ui;

import cz.cuni.lf1.lge.ThunderSTORM.calibration.DefocusFunction;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.IEstimator;

public interface ICalibrationEstimatorUI {

    IEstimator getImplementation();
    int getFitradius();
    void setAngle(double angle);
    void setDefocusModel(DefocusFunction defocusModel);
}
