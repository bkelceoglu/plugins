package io.subutai.plugin.sqoop.ui.wizard;


import java.util.concurrent.ExecutorService;

import javax.naming.NamingException;

import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.hadoop.api.Hadoop;
import io.subutai.plugin.hadoop.api.HadoopClusterConfig;
import io.subutai.plugin.sqoop.api.Sqoop;
import io.subutai.plugin.sqoop.api.SqoopConfig;

import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;


public class Wizard
{

    private final GridLayout grid;
    private final Sqoop sqoop;
    private final Hadoop hadoop;
    private final Tracker tracker;
    private final EnvironmentManager environmentManager;
    private final ExecutorService executorService;
    private int step = 1;
    private SqoopConfig config = new SqoopConfig();
    private HadoopClusterConfig hadoopConfig = new HadoopClusterConfig();


    public Wizard( ExecutorService executorService, Sqoop sqoop, Hadoop hadoop, Tracker tracker, EnvironmentManager environmentManager ) throws NamingException
    {

        this.executorService = executorService;
        this.sqoop = sqoop;
        this.hadoop = hadoop;
        this.tracker = tracker;
        this.environmentManager = environmentManager;

        grid = new GridLayout( 1, 20 );
        grid.setMargin( true );
        grid.setSizeFull();

        putForm();
    }


    private void putForm()
    {
        grid.removeComponent( 0, 1 );
        Component component = null;
        switch ( step )
        {
            case 1:
            {
                component = new WelcomeStep( this );
                break;
            }
            case 2:
            {
                component = new NodeSelectionStep( hadoop, environmentManager, this );
                break;
            }
            case 3:
            {
                component = new VerificationStep( sqoop, hadoop, executorService, tracker, environmentManager, this );
                break;
            }
            default:
            {
                break;
            }
        }

        if ( component != null )
        {
            grid.addComponent( component, 0, 1, 0, 19 );
        }
    }


    public Component getContent()
    {
        return grid;
    }


    protected void next()
    {
        step++;
        putForm();
    }


    protected void back()
    {
        step--;
        putForm();
    }


    protected void init()
    {
        step = 1;
        config = new SqoopConfig();
        hadoopConfig = new HadoopClusterConfig();
        putForm();
    }


    public SqoopConfig getConfig()
    {
        return config;
    }


    public HadoopClusterConfig getHadoopConfig()
    {
        return hadoopConfig;
    }

    public Sqoop getSqoopManager()
    {
        return sqoop;
    }
}

