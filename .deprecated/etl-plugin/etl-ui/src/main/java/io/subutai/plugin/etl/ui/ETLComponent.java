package io.subutai.plugin.etl.ui;


import java.util.concurrent.ExecutorService;

import javax.naming.NamingException;

import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.VerticalLayout;

import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.etl.api.ETL;
import io.subutai.plugin.etl.ui.extract.ETLExtractManager;
import io.subutai.plugin.etl.ui.load.ETLLoadManager;
import io.subutai.plugin.etl.ui.transform.ETLTransformManager;
import io.subutai.plugin.hadoop.api.Hadoop;
import io.subutai.plugin.hive.api.Hive;
import io.subutai.plugin.pig.api.Pig;
import io.subutai.plugin.sqoop.api.Sqoop;


public class ETLComponent extends CustomComponent
{

    private final TabSheet sheet;


    public ETLComponent( ExecutorService executorService, ETL etl, Hadoop hadoop, Hive hive, Pig pig, Sqoop sqoop,
                         Tracker tracker, EnvironmentManager environmentManager ) throws NamingException
    {
        final ETLExtractManager etlExtractManager =
                new ETLExtractManager( executorService, etl, hadoop, sqoop, tracker, environmentManager );

        final ETLLoadManager etlLoadManager =
                new ETLLoadManager( executorService, etl, hadoop, sqoop, tracker, environmentManager );

        final ETLTransformManager etlTransformManager =
                new ETLTransformManager( executorService, etl, hadoop, sqoop, tracker, hive, pig, environmentManager );


        setSizeFull();
        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.setSpacing( true );
        verticalLayout.setSizeFull();

        sheet = new TabSheet();
        sheet.setSizeFull();
        sheet.addTab( etlExtractManager.getContent(), "Extract" );
        sheet.getTab( 0 ).setId( "etlExtractManagerTab" );

        sheet.addTab( etlTransformManager.getContent(), "Transform" );
        sheet.getTab( 1 ).setId( "etlTransformManagerTab" );

        sheet.addTab( etlLoadManager.getContent(), "Load" );
        sheet.getTab( 2 ).setId( "etlLoadManagerTab" );

        sheet.addSelectedTabChangeListener( new TabSheet.SelectedTabChangeListener()
        {
            @Override
            public void selectedTabChange( TabSheet.SelectedTabChangeEvent event )
            {
                TabSheet tabsheet = event.getTabSheet();
                String caption = tabsheet.getTab( event.getTabSheet().getSelectedTab() ).getCaption();
            }
        } );

        verticalLayout.addComponent( sheet );
        setCompositionRoot( verticalLayout );
    }
}
