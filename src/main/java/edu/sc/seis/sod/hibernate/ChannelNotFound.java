// **********************************************************************
//
// Generated by the ORBacus IDL to Java Translator
//
// Copyright (c) 2000
// Object Oriented Concepts, Inc.
// Billerica, MA, USA
//
// All Rights Reserved
//
// **********************************************************************

// Version: 4.0.5

package edu.sc.seis.sod.hibernate;

import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.sod.model.station.ChannelId;


final public class ChannelNotFound extends Exception
{
    public
    ChannelNotFound(String reason, Channel chan)
    {
        super(reason);
        this.channel = ChannelId.of(chan);
    }
    
    public
    ChannelNotFound(Channel chan)
    {
        this.channel = ChannelId.of(chan);
    }

    public
    ChannelNotFound(ChannelId channel)
    {
        this.channel = channel;
    }

    public
    ChannelNotFound(String reason,
                    ChannelId channel)
    {
        super(reason);
        this.channel = channel;
    }

    public ChannelId channel;
}
