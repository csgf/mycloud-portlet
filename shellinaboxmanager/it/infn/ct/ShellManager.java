/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package it.infn.ct;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author maurizio
 */
public class ShellManager {
    private static Properties configuration;
    final private ProcessBuilder pb;
    private Process shellInABoxProcess = null;
    final Map<String, URL> vmShells; 
    private final String certDir;
    private final String serverCommand;
    private final Integer port;
    private static boolean configured = false;
    private static ShellManager instance = null;
    
    
    /**
     *  
     * @param conf Properties:
     * certDir: directory dei certificati per SSL
     * workingDir: working directory (puo'essere null)
     * serverCommand: comando da lanciare (shellinabox)
     * port: porta tcp per shellinabox (puo' essere null- usa default 4200)
     */
    public static void configure(Properties conf)
    {
        configuration = new Properties(conf);
        instance = null;
    }
    
    public static ShellManager getInstance()
    {
       if(instance == null)
       {
           instance = new ShellManager(configuration.getProperty("certDir"),
                                       configuration.getProperty("workingDir"),
                                       configuration.getProperty("serverCommand"),
                                       (configuration.getProperty("port") != null ? Integer.parseInt(configuration.getProperty("port")) : null)
                   
                                       
                   );
       }
       return instance;
    }
    
    
    private ShellManager()
    {
        this(null,".","shellinaboxd", null);
    }
    
    
    private ShellManager(String cDir, String workingDirectory, String serverCommand, Integer port)
    {
        pb = new ProcessBuilder();
        pb.directory(new File(workingDirectory != null ? workingDirectory : "."));
        vmShells = new HashMap<String, URL>();
        certDir = cDir;
        this.serverCommand = serverCommand;
        this.port = port;
        System.out.println("SHelLManager instace: " + this);
    }
    
    public void addShell(String id, String resource, String host, Integer port) throws MalformedURLException
    {
        if(id!=null && host!=null && port!=null && resource != null)
        {
            vmShells.put(id, new URL("http", host, port, resource)); //fake protocol
        }
        
    }
    
    public void delShell(String id)
    {
        vmShells.remove(id);
    }
    
    public Map<String , URL> getShells()
    {
        return new HashMap(vmShells);
    }
    
    public void startShellServer() throws IOException
    {
        if(this.shellInABoxProcess!=null)
            this.shellInABoxProcess.destroy();
        this.shellInABoxProcess = null;
        
        if(this.vmShells.isEmpty())
        {
            return;
        }
        List<String> commands = new ArrayList<String> ();
        commands.add(this.serverCommand);
        
        
        if(this.port !=null)
        {
            commands.add("-p" + this.port);
        }
        
        if(this.certDir !=null)
        {
            commands.add("-c" + this.certDir);
            System.out.println(this.certDir);
        }
        
        for(URL shellURL : vmShells.values())
        {
            String ss = this.createServiceString(shellURL);
            commands.add("-s" + ss);
            System.out.println(ss);
        }
        
        pb.command(commands);
        
        this.shellInABoxProcess = pb.start();
        
    }

    private String createServiceString(URL shellURL) {
        StringBuilder service = new StringBuilder("/").
                                        append(shellURL.getFile()).
                                        append(":").
                                        //append(shellURL.getProtocol()).
                                        append("SSH").
                                        append(":").
                                        append(shellURL.getHost()).append(":").append(shellURL.getPort());
        return service.toString();
        
    }

    public int waitServerShutdown() throws InterruptedException {
        if(this.shellInABoxProcess != null)
        {
            return this.shellInABoxProcess.waitFor();
        }
        else
        {
            return 0;
        }
    }

    public void redirectStreams(boolean b) throws IOException {
        pb.redirectErrorStream(b);
        File f = new File("output");
        f.createNewFile();
       // pb.redirectOutput(f);
        
    }

  
    
    
}
