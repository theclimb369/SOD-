package edu.sc.seis.sod.velocity.network;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import edu.iris.Fissures.IfNetwork.Station;
import edu.iris.Fissures.IfNetwork.StationId;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.QuantityImpl;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.StationIdUtil;
import edu.sc.seis.fissuresUtil.bag.DistAz;
import edu.sc.seis.sod.status.FissuresFormatter;
import edu.sc.seis.sod.velocity.event.VelocityEvent;

/**
 * @author groves Created on Jan 7, 2005
 */
public class VelocityStation extends Station {

    public VelocityStation(Station sta) {
        this(sta, -1);
    }

    public VelocityStation(Station sta, int dbid) {
        this.dbid = dbid;
        this.sta = sta;
        name = sta.name;
        my_location = sta.my_location;
        effective_time = sta.effective_time;
        operator = sta.operator;
        description = sta.description;
        comment = sta.comment;
        my_network = sta.my_network;
    }

    public int getDbId() {
        if(dbid >= 0) {
            return dbid;
        }
        throw new UnsupportedOperationException("This station had no dbid");
    }

    public void setDbId(int dbid) {
        this.dbid = dbid;
    }

    public StationId get_id() {
        return sta.get_id();
    }

    public String get_code() {
        return sta.get_code();
    }

    public String getCode() {
        return get_code();
    }

    public String getNetCode() {
        return getNet().get_code();
    }

    public VelocityNetwork getNet() {
        if(velocityNet == null) {
            velocityNet = new VelocityNetwork(my_network);
        }
        return velocityNet;
    }

    public MicroSecondDate getStartDate() {
        return new MicroSecondDate(effective_time.start_time);
    }

    public MicroSecondDate getEndDate() {
        return new MicroSecondDate(effective_time.end_time);
    }

    public String getStart() {
        return FissuresFormatter.formatDate(effective_time.start_time);
    }

    public String getStart(String dateFormat) {
        if(dateFormat.equals("longfile")) {
            return FissuresFormatter.formatDateForFile(effective_time.start_time);
        }
        return new SimpleDateFormat(dateFormat).format(new MicroSecondDate(effective_time.start_time));
    }

    public String getEnd() {
        return FissuresFormatter.formatDate(sta.effective_time.end_time);
    }

    public String getName() {
        return name;
    }

    public String getLatitude() {
        return df.format(sta.my_location.latitude);
    }

    public String getLongitude() {
        return df.format(sta.my_location.longitude);
    }

    public String getOrientedLatitude() {
        if(sta.my_location.latitude < 0) {
            return df.format(-sta.my_location.latitude) + " S";
        } else {
            return df.format(sta.my_location.latitude) + " N";
        }
    }

    public String getOrientedLongitude() {
        if(sta.my_location.longitude < 0) {
            return df.format(-sta.my_location.longitude) + " W";
        } else {
            return df.format(sta.my_location.longitude) + " E";
        }
    }

    public String getDepth() {
        return FissuresFormatter.formatDepth(QuantityImpl.createQuantityImpl(sta.my_location.depth));
    }

    public String getDistance(VelocityEvent event) {
        double km = DistAz.degreesToKilometers(new DistAz(this, event).getDelta());
        return FissuresFormatter.formatDistance(new QuantityImpl(km,
                                                                 UnitImpl.KILOMETER));
    }

    public String getDistanceDeg(VelocityEvent event) {
        return FissuresFormatter.formatDistance(getDist(event));
    }

    public String getAz(VelocityEvent event) {
        double az = new DistAz(this, event).getAz();
        return FissuresFormatter.formatQuantity(new QuantityImpl(az,
                                                                 UnitImpl.DEGREE));
    }

    public QuantityImpl getDist(VelocityEvent event) {
        double deg = new DistAz(this, event).getDelta();
        return new QuantityImpl(deg, UnitImpl.DEGREE);
    }

    public String getBaz(VelocityEvent event) {
        double baz = new DistAz(this, event).getBaz();
        return FissuresFormatter.formatQuantity(new QuantityImpl(baz,
                                                                 UnitImpl.DEGREE));
    }

    public boolean equals(Object o) {
        if(o == this) {
            return true;
        }
        if(o instanceof VelocityStation) {
            VelocityStation oVel = (VelocityStation)o;
            if(oVel.dbid != -1 && dbid != -1 && oVel.dbid == dbid) {
                return true;
            }
        }
        if(o instanceof Station) {
            Station oSta = (Station)o;
            return StationIdUtil.areEqual(oSta, sta);
        }
        return false;
    }

    public int hashCode() {
        return StationIdUtil.toString(get_id()).hashCode();
    }

    private VelocityNetwork velocityNet = null;

    private Station sta;

    private int dbid = -1;

    private int[] position;

    public void setPosition(int[] position) {
        this.position = position;
    }

    public int[] getPosition() {
        return position;
    }

    private DecimalFormat df = new DecimalFormat("0.00");
}