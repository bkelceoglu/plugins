/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.subutai.plugin.accumulo.rest;


import java.util.Set;


/**
 * @author dilshat
 */
public class TrimmedAccumuloConfig
{

    private String clusterName = "";
    private String instanceName = "";
    private String password = "";
    private String masterNode;
    private String gcNode;
    private String monitor;
    private Set<String> tracers;
    private Set<String> slaves;
    private String hadoopClusterName = "";
    private String zkClusterName = "";


    public String getClusterName()
    {
        return clusterName;
    }


    public void setClusterName( final String clusterName )
    {
        this.clusterName = clusterName;
    }


    public String getInstanceName()
    {
        return instanceName;
    }


    public void setInstanceName( final String instanceName )
    {
        this.instanceName = instanceName;
    }


    public String getPassword()
    {
        return password;
    }


    public void setPassword( final String password )
    {
        this.password = password;
    }


    public String getMasterNode()
    {
        return masterNode;
    }


    public void setMasterNode( final String masterNode )
    {
        this.masterNode = masterNode;
    }


    public String getGcNode()
    {
        return gcNode;
    }


    public void setGcNode( final String gcNode )
    {
        this.gcNode = gcNode;
    }


    public String getMonitor()
    {
        return monitor;
    }


    public void setMonitor( final String monitor )
    {
        this.monitor = monitor;
    }


    public Set<String> getTracers()
    {
        return tracers;
    }


    public void setTracers( final Set<String> tracers )
    {
        this.tracers = tracers;
    }


    public Set<String> getSlaves()
    {
        return slaves;
    }


    public void setSlaves( final Set<String> slaves )
    {
        this.slaves = slaves;
    }


    public String getHadoopClusterName()
    {
        return hadoopClusterName;
    }


    public void setHadoopClusterName( final String hadoopClusterName )
    {
        this.hadoopClusterName = hadoopClusterName;
    }


    public String getZkClusterName()
    {
        return zkClusterName;
    }


    public void setZkClusterName( final String zkClusterName )
    {
        this.zkClusterName = zkClusterName;
    }
}
