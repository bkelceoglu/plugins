package org.safehaus.subutai.plugin.hbase.cli;


import java.util.UUID;

import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.environment.EnvironmentNotFoundException;
import org.safehaus.subutai.common.tracker.OperationState;
import org.safehaus.subutai.common.tracker.TrackerOperationView;
import org.safehaus.subutai.core.env.api.EnvironmentManager;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.api.NodeState;
import org.safehaus.subutai.plugin.hbase.api.HBase;
import org.safehaus.subutai.plugin.hbase.api.HBaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;


@Command( scope = "hbase", name = "check-cluster", description = "Checks cluster nodes' statutes" )
public class CheckClusterCommand extends OsgiCommandSupport
{
    @Argument( index = 0, name = "clusterName", description = "The name of the cluster.", required = true,
            multiValued = false )
    String clusterName = null;
    private Tracker tracker;
    private HBase hbaseManager;
    private EnvironmentManager environmentManager;
    private static final Logger LOG = LoggerFactory.getLogger( CheckClusterCommand.class.getName() );


    @Override
    protected Object doExecute() throws Exception
    {
        HBaseConfig config = hbaseManager.getCluster( clusterName );
        Environment environment;
        try{
            environment = environmentManager.findEnvironment( config.getEnvironmentId() );

            for ( UUID uuid : config.getAllNodes() ){

                String hostname = environment.getContainerHostById( uuid ).getHostname();
                UUID checkUUID = hbaseManager.checkNode( clusterName, hostname );
                StringBuilder sb = new StringBuilder();
                sb.append( "Status on ").append( hostname ).append( ": \n" );
                sb.append( waitUntilOperationFinish( tracker, checkUUID ) );
                System.out.println( sb.toString() );
            }
        }
        catch ( EnvironmentNotFoundException e ){
            LOG.error( "Could not find environment !", e );
        }
        return null;
    }


    protected static String waitUntilOperationFinish( Tracker tracker, UUID uuid )
    {
        long start = System.currentTimeMillis();
        while ( !Thread.interrupted() )
        {
            TrackerOperationView po = tracker.getTrackerOperation( HBaseConfig.PRODUCT_KEY, uuid );
            if ( po != null )
            {
                if ( po.getState() != OperationState.RUNNING )
                {
                    return po.getLog();
                }
            }
            try
            {
                Thread.sleep( 1000 );
            }
            catch ( InterruptedException ex )
            {
                break;
            }
            if ( System.currentTimeMillis() - start > ( 90 * 1000 ) )
            {
                break;
            }
        }
        return null;
    }


    public Tracker getTracker()
    {
        return tracker;
    }


    public void setTracker( final Tracker tracker )
    {
        this.tracker = tracker;
    }


    public HBase getHbaseManager()
    {
        return hbaseManager;
    }


    public void setHbaseManager( final HBase hbaseManager )
    {
        this.hbaseManager = hbaseManager;
    }


    public EnvironmentManager getEnvironmentManager()
    {
        return environmentManager;
    }


    public void setEnvironmentManager( final EnvironmentManager environmentManager )
    {
        this.environmentManager = environmentManager;
    }
}
