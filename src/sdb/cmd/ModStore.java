/*
 * (c) Copyright 2006, 2007 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package sdb.cmd;

import java.sql.SQLException;
import java.util.List;

import arq.cmd.TerminationException;
import arq.cmdline.ArgDecl;
import arq.cmdline.CmdArgModule;
import arq.cmdline.CmdGeneral;
import arq.cmdline.ModBase;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sdb.SDBException;
import com.hp.hpl.jena.sdb.SDBFactory;
import com.hp.hpl.jena.sdb.sql.MySQLEngineType;
import com.hp.hpl.jena.sdb.sql.SDBConnection;
import com.hp.hpl.jena.sdb.sql.SDBExceptionSQL;
import com.hp.hpl.jena.sdb.store.DatasetStore;
import com.hp.hpl.jena.sdb.store.LayoutType;
import com.hp.hpl.jena.sdb.store.Store;
import com.hp.hpl.jena.sdb.store.StoreDesc;
import com.hp.hpl.jena.sdb.store.StoreFactory;
import com.hp.hpl.jena.shared.NotFoundException;
import com.hp.hpl.jena.util.FileManager;

/** Construction of a store from a store description,
 * possibly modified by command line arguments.
 * @author Andy Seaborne
 * @version $Id: CmdStore.java,v 1.1 2006/04/22 19:51:12 andy_seaborne Exp $
 */ 

public class ModStore extends ModBase
{
    // -------- This ...
    protected final ArgDecl argDeclSDBdesc       = new ArgDecl(true, "sdb", "store");
    protected final ArgDecl argDeclSDBdescList   = new ArgDecl(true, "storelist", "storeList", "stores");
    
    // ---- modified by these .... makes a connection description
    protected final ArgDecl argDeclJdbcURL      = new ArgDecl(true, "jdbc");
    protected final ArgDecl argDeclJdbcDriver   = new ArgDecl(true, "jdbcDriver", "jdbcdriver");

    protected final ArgDecl argDeclDbHost       = new ArgDecl(true, "dbHost", "dbhost");
    protected final ArgDecl argDeclDbName       = new ArgDecl(true, "dbName", "db");
    
    protected final ArgDecl argDeclDbType      = new ArgDecl(true, "dbType", "dbtype");
    protected final ArgDecl argDeclDbArgs      = new ArgDecl(true, "dbArgs", "dbargs");
    
    protected final ArgDecl argDeclDbUser      = new ArgDecl(true, "dbUser", "user");
    protected final ArgDecl argDeclDbPassword  = new ArgDecl(true, "dbPassword", "password", "pw");

    // Store modifiers
    
    protected final ArgDecl argDeclLayout       = new ArgDecl(true, "layout");
    protected final ArgDecl argDeclMySQLEngine  = new ArgDecl(true, "engine");

    
//    protected String driverName = null;      // JDBC class name
    StoreDesc storeDesc = null ;
    SDBConnection connection = null ;
    boolean connectionAttempted = false ;
    Store store = null ;
    Dataset dataset = null ;
    Model model = null ;
    List<String> loadFiles = null ;
    boolean formatFirst = false ; 

    public ModStore()
    {
        SDBConnection.logSQLExceptions = true ;
    }
    
    public void registerWith(CmdGeneral cmdLine)
    {
        final boolean AddUsage = false ;
        
        cmdLine.getUsage().startCategory("Store and connection") ;
        
        cmdLine.add(argDeclSDBdesc,
                    "--sdb=<file>", "Store and connection description") ;
        
        cmdLine.add(argDeclLayout,
                    "--layout=NAME", "Database schema") ;
        
        // Connection-level
        cmdLine.add(argDeclJdbcURL);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--jdbc", "JDBC URL") ;
        
        cmdLine.add(argDeclJdbcDriver);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--jdbcDriver=", "JDBC driver class name") ;
        
        cmdLine.add(argDeclDbHost);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbHost=", "DB Host") ;

        cmdLine.add(argDeclDbName);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbName=", "Database name") ;
        
        cmdLine.add(argDeclDbArgs);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbArgs=", "Additional arguments for JDBC URL") ;

        cmdLine.add(argDeclDbType);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbType=", "Database type") ;

        cmdLine.add(argDeclDbUser);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbUser=", "Database user") ;

        cmdLine.add(argDeclDbPassword);
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--dbPassword", "Daatbase user password") ;

        // Store
        cmdLine.add(argDeclMySQLEngine) ;
        if ( AddUsage )
            cmdLine.getUsage().addUsage("--engine=", "MySQL engine type") ;
        
//        cmdLine.add(argDeclLoad) ;
//        if ( AddUsage )
//            cmdLine.getUsage().addUsage("--load=", "Datafile to load (permanent : for in-memory stores only) ") ;
//        
//        cmdLine.add(argDeclFormat) ;
//        if ( AddUsage )
//            cmdLine.getUsage().addUsage("--format", "Format first(permanent : for in-memory stores only) ") ;
        
    }
    
    
    @SuppressWarnings("unchecked")
    public void processArgs(CmdArgModule cmdLine)
    {
        if (! cmdLine.contains(argDeclSDBdesc))
        {
            System.err.println("No store description");
            throw new TerminationException(1);
        }
        
        String f = cmdLine.getArg(argDeclSDBdesc).getValue() ;
        try {
            storeDesc = StoreDesc.read(f) ;
            if ( storeDesc.getLayout() == null )
            {
                System.err.println("No layout or unrecognized layout");
                throw new TerminationException(1);
            }
            
        } catch (SDBException ex)
        {
            System.err.println("Failed to read the store description");
            System.err.println(ex.getMessage()) ;
            throw new TerminationException(1) ;
        }
        catch (NotFoundException ex)
        {
            System.err.println(f+" : Store description not found");
            throw new TerminationException(1) ;
        }
        
        // Overrides.
        if (cmdLine.contains(argDeclDbHost))
            storeDesc.connDesc.setHost(cmdLine.getArg(argDeclDbHost).getValue());
        
        if (cmdLine.contains(argDeclDbName))
            storeDesc.connDesc.setName(cmdLine.getArg(argDeclDbName).getValue()) ;
        
        if (cmdLine.contains(argDeclDbType))
            storeDesc.connDesc.setType(cmdLine.getArg(argDeclDbType).getValue()) ;

        if (cmdLine.contains(argDeclDbArgs))
            storeDesc.connDesc.setArgStr(cmdLine.getArg(argDeclDbArgs).getValue()) ;

        if (cmdLine.contains(argDeclDbUser))
            storeDesc.connDesc.setUser(cmdLine.getArg(argDeclDbUser).getValue()) ;

        if (cmdLine.contains(argDeclDbPassword))
            storeDesc.connDesc.setPassword(cmdLine.getArg(argDeclDbPassword).getValue()) ;

        if (cmdLine.contains(argDeclMySQLEngine))
            storeDesc.engineType = MySQLEngineType.convert(cmdLine.getArg(argDeclMySQLEngine).getValue());
        
        if (cmdLine.contains(argDeclLayout))
        {
            String layoutName = cmdLine.getArg(argDeclLayout).getValue() ;
            storeDesc.setLayout(LayoutType.convert(layoutName)) ;

            if ( storeDesc.getLayout() == null )
            {
                System.err.println("Don't recognize layout name '"+layoutName+"'") ;
                throw new TerminationException(2) ;
            }
        }

        //storeDesc.connDesc.initJDBC() ;
        
        if ( false )
        {
            //System.out.println("URL       = " + storeDesc.connDesc.URL);
            System.out.println("Type      = " + storeDesc.connDesc.getType());
            System.out.println("Host      = " + storeDesc.connDesc.getHost());
            System.out.println("Database  = " + storeDesc.connDesc.getName());
            System.out.println("User      = " + storeDesc.connDesc.getUser());
            System.out.println("Password  = " + storeDesc.connDesc.getPassword());
            if ( storeDesc.connDesc.getArgStr() != null )
                System.out.println("Args      = " + storeDesc.connDesc.getArgStr());
                
            System.out.println("Layout    = " + storeDesc.getLayout().getName()) ;
            //System.out.println("Name      = " + argModelName);

            SDBConnection.logSQLExceptions = true ;
            SDBConnection.logSQLStatements = true ;
        }

        if (cmdLine.contains(argDeclJdbcDriver))
        {
            String driverName = cmdLine.getArg(argDeclJdbcDriver).getValue();
            storeDesc.connDesc.setDriver(driverName) ;
        }
    }
    
    public Store getStore()
    { 
        if ( store == null )
        {
            store = StoreFactory.create(getConnection(), storeDesc) ;
            
            if ( formatFirst )
                getStore().getTableFormatter().format() ;
        }
        return store ;
    }

    public boolean hasStore() { return store != null ; }
    
    public StoreDesc getStoreDesc()
    {
        return storeDesc ;
    }

    public void setDbName(String dbName)
    {
        // used by truncate and format.
        storeDesc.connDesc.setName(dbName) ;
    }
    
    
    public Dataset getDataset()
    { 
        if ( dataset == null )
        {
            dataset = DatasetStore.create(getStore()) ;
            initData(dataset.getDefaultModel()) ;
        }
        
        return dataset ;
    }
    
//    public Model getModel()
//    {
//        if ( model == null )
//        {
//            model = SDBFactory.connectDefaultModel(getStore()) ;
//            initData(model) ;
//        }
//        return model ;
//    }
    
    private void initData(Model model)
    {
        if ( loadFiles != null )
        {
            
            for ( String s : loadFiles )
                FileManager.get().readModel(model, s) ;
        }
        loadFiles = null ;
    }
    
//    public Graph getGraph()
//    {
//        return getModel().getGraph() ;
//    }
    
    public boolean isConnected() { return connection != null ; }
    public SDBConnection getConnection()
    {
        if ( ! isConnected() && ! connectionAttempted )
            try {
                connection = SDBFactory.createConnection(storeDesc.connDesc) ;
            } finally { connectionAttempted = true ; }
        return connection ;
    }

    boolean hsqlDetech = false ;
    boolean isHSQL = false ;
    
    public boolean isHSQL()
    {
        if ( !hsqlDetech )
        {
            try {
                isHSQL = getConnection().getSqlConnection().getMetaData().getDatabaseProductName().contains("HSQL") ;
            } catch (SQLException ex)
            { throw new SDBExceptionSQL(ex) ; }
        }
        return isHSQL ;
    }
    
    public void closedown()
    {
        if ( store != null )
            store.close() ;
    }
}

/*
 * (c) Copyright 2006, 2007 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */