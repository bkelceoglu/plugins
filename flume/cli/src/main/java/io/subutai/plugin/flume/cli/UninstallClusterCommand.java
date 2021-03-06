package io.subutai.plugin.flume.cli;


import java.util.UUID;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

import io.subutai.common.tracker.OperationState;
import io.subutai.common.tracker.TrackerOperationView;
import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.flume.api.Flume;
import io.subutai.plugin.flume.api.FlumeConfig;


/**
 * Displays the last log entries
 */
@Command( scope = "flume", name = "uninstall-cluster", description = "Command to uninstall Flume cluster" )
public class UninstallClusterCommand extends OsgiCommandSupport
{

    @Argument( index = 0, name = "clusterName", description = "The name of the cluster.", required = true,
            multiValued = false )
    String clusterName = null;
    private Flume flumeManager;
    private Tracker tracker;


    @Override
    protected Object doExecute()
    {
        UUID uuid = flumeManager.uninstallCluster( clusterName );
        int logSize = 0;
        while ( !Thread.interrupted() )
        {
            TrackerOperationView po = tracker.getTrackerOperation( FlumeConfig.PRODUCT_KEY, uuid );
            if ( po != null )
            {
                if ( logSize != po.getLog().length() )
                {
                    System.out.print( po.getLog().substring( logSize, po.getLog().length() ) );
                    System.out.flush();
                    logSize = po.getLog().length();
                }
                if ( po.getState() != OperationState.RUNNING )
                {
                    break;
                }
            }
            else
            {
                System.out.println( "Product operation not found. Check logs" );
                break;
            }
            try
            {
                Thread.sleep( 1000 );
            }
            catch ( InterruptedException ex )
            {
                break;
            }
        }
        return null;
    }


    public void setTracker( Tracker tracker )
    {
        this.tracker = tracker;
    }


    public void setFlumeManager( Flume flumeManager )
    {
        this.flumeManager = flumeManager;
    }
}
