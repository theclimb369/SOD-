/**
 * UpdateChecker.java
 *
 * @author Philip Crotwell
 */

package edu.sc.seis.sod;

import edu.sc.seis.fissuresUtil.cache.JobTracker;
import edu.sc.seis.fissuresUtil.cache.WorkerThreadPool;
import edu.sc.seis.fissuresUtil.chooser.UpdateCheckerJob;
import edu.sc.seis.gee.configurator.DefaultParamNames;

public class UpdateChecker  {

    public UpdateChecker() {
        UpdateCheckerJob job = new UpdateCheckerJob("SOD",
                                                    updateURL,
                                                    GUI_BASED,
                                                    showNoUpdate);
        JobTracker.getTracker().add(job);
        WorkerThreadPool.getDefaultPool().invokeLater(job);
    }

    public static final String updateURL = "http://www.seis.sc.edu/SOD/UpdateChecker.xml";

    public static final boolean GUI_BASED = false;

    public static final boolean showNoUpdate = true;
}

