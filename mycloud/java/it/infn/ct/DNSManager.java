package it.infn.ct;

/**
 * @author emidio
 *
 */


import com.liferay.portal.kernel.log.Log; 
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.xbill.DNS.*;

public class DNSManager {

	private SimpleResolver res; // il server DNS, authz per la zona, cui viene mandato il msg di update
	private Name zone;	 		// la zona in questione
	private static Log log = LogFactoryUtil.getLog(DNSManager.class);
  //private static Map<String,String> addr2nameMap = new HashMap<String,String>();
    private static SQLiteDB localReverseDNS = new SQLiteDB();

	public DNSManager (String authoritativeNameServer, String keyname, String key, String zoneString){

		try {
			this.zone=Name.fromString(zoneString);
			this.res=new SimpleResolver(authoritativeNameServer);
			res.setTSIGKey(new TSIG(keyname,key));
			res.setTCP(true);
		}
		
		catch (Exception e) {
			log.error("Error: unable to initiate DNS Manager: "+e.getMessage());
		}
	}
	
	public String updateEntry(String hostString, String IP){

		Update update;
		Message response;
		Name host;
		String outputMSG="";
		
		try {
			host=Name.fromString(hostString,zone);
			update=new Update(zone);
			update.replace(host,Type.A,36,IP);
			outputMSG="Replacing "+host.toString()+" "+IP;
	/* CHANGE ME */	
    // addr2nameMap.put(IP, hostString);
	  if (this.localReverseDNS.update(host.toString(), IP)){
		  log.debug("[Info] Local DB updated");
	  } else {
		  log.error("[Warning] Failed to update local DB");
	  }
		  
      response=res.send(update); 
	  outputMSG+=response.toString();
      log.debug(outputMSG);
	  return outputMSG;
	}
		catch (TextParseException tPEx){
			outputMSG+=tPEx.getMessage();
			log.debug(outputMSG);
			return outputMSG;
		}
		catch (IOException iOEx){
			outputMSG+=iOEx.getMessage();
			log.debug(outputMSG);
			return outputMSG;
		}
		
	}
  /*
   * REPLACED BY SQLITE ? 
  private class ValueEquals implements Predicate< Map.Entry<String,String> > {

    private String value = null;
   
    public ValueEquals(String v) {
      this.value = v;
    }
 
    @Override
    public boolean apply(Entry<String, String> t) {
      return t.getValue().equals(value);
    }
  }
  */ 
	
  public static String getHostByAddr(String ip) {
    //return  addr2nameMap.get(ip);
    String result = localReverseDNS.Host2IP(ip);
	  return result == null ? "" : result.substring(0,result.length()-1);
  }
  
  public String removeEntry(String hostString){

		Update update;
		Message response;
		Name host;
		String outputMSG="";
		
		try {
			host=Name.fromString(hostString,zone);
			update=new Update(zone);
			update.delete(host,Type.A);
			outputMSG="Removing "+host.toString();
      
	  /* CHANGE ME */
      //addr2nameMap.remove(Iterables.find(
      //    addr2nameMap.entrySet(), 
      //    new ValueEquals(hostString)
      //).getKey());
	  this.localReverseDNS.remove(host.toString());

      response=res.send(update);
			outputMSG+=response.toString();
			log.debug(outputMSG);
      return outputMSG;
		}
		catch (TextParseException tPEx){
			outputMSG+=tPEx.getMessage();
      log.debug(outputMSG);
			return outputMSG;
		}
		catch (IOException iOEx){
			outputMSG+=iOEx.getMessage();
      log.debug(outputMSG);
			return outputMSG;
		}
		
	}
  
  private static class SQLiteDB {
		/* keeps track of entries inserted and
		 * fakes reverse DNS service
		 * */
	    Connection c = null;
	    
	    public SQLiteDB(){
	    	
	    Statement stmt = null;
	    try {
	      Class.forName("org.sqlite.JDBC");
	      // va parametrizzato ?? 
	      c = DriverManager.getConnection("jdbc:sqlite:localReverseDNS.db");
	      log.debug("Opened database successfully");

	      stmt = c.createStatement();
	      String createTable = "CREATE TABLE IF NOT EXISTS HOSTIP " +
	                   "(ID INTEGER PRIMARY KEY   AUTOINCREMENT," +
	                   " HOSTNAME       TEXT    NOT NULL, " + 
	                   " IPADDR         TEXT    NOT NULL " + 
	                   " )"; 
	      stmt.executeUpdate(createTable);
	      stmt.close();
	      c.close();
	    } catch ( Exception e ) {
	      log.error( e.getClass().getName() + ": " + e.getMessage() );
	    }
	    log.debug("Table created successfully");
	}
	    public Boolean update(String hostname, String IP){
	    	
	    	String sql="";
	    	try {
          
	    		c = DriverManager.getConnection("jdbc:sqlite:localReverseDNS.db");
	    		Statement updateStmt=c.createStatement();
	    		if (hostExists(hostname)){
	    			sql= "UPDATE HOSTIP SET IPADDR=\""+IP+"\" WHERE HOSTNAME=\""+hostname+"\"";
	    		} else {
	    			sql= "INSERT INTO HOSTIP VALUES (null,\""+hostname+"\",\""+IP+"\")";
	    		}
	    		log.debug("Update SQL String: "+sql);
	    		updateStmt.executeUpdate(sql);
	    		updateStmt.close();
	    		c.close();
	    		return true;
	    	} catch ( Exception e ) {
	  	      log.error( e.getClass().getName() + ": " + e.getMessage() );
		      return false;
		    }
	    }
	    public Boolean remove(String hostname){
	    	String sql="";
	    	try {
	    		c = DriverManager.getConnection("jdbc:sqlite:localReverseDNS.db");
	    		Statement updateStmt=c.createStatement();
	    		
	    		sql= "DELETE FROM HOSTIP WHERE HOSTNAME=\""+hostname+"\"";
	    		
	    		log.debug("Delete SQL command : "+sql);
	    		updateStmt.executeUpdate(sql);
	    		updateStmt.close();
	    		c.close();
	    		return true;
	    	} catch ( Exception e ) {
	  	      log.error( e.getClass().getName() + ": " + e.getMessage() );
		      return false;
		    }
	    }
	    public String Host2IP(String IP){
	   
	    	String host=null;
	    	try {
	    		c = DriverManager.getConnection("jdbc:sqlite:localReverseDNS.db");
	    		Statement selectStmt=c.createStatement();
	    		String selectString="SELECT HOSTNAME FROM HOSTIP "+	
		    			"WHERE IPADDR=\""+IP+"\"";
		    	log.debug("Select SQL String "+selectString);
	    		ResultSet rs= selectStmt.executeQuery(selectString);
	    		while (rs.next()) {
	    			host=rs.getString("HOSTNAME");
	    		}
	    		rs.close();
	    		selectStmt.close();
	    		c.close();
	    		
	    	} catch ( Exception e ) {
	  	      log.error( e.getClass().getName() + ": " + e.getMessage() );
		      host="[ERROR] SQLITE Search failed: Record for IP "+IP+" not found";
		    }
	    	return host;
	    }
	    
	    private Boolean hostExists(String hostname){
	    	
	    	Boolean itExists=false; 
	    	
	    	try {
	    		c = DriverManager.getConnection("jdbc:sqlite:localReverseDNS.db");
	    		Statement selectStmt=c.createStatement();
	    		String selectString="SELECT IPADDR FROM HOSTIP "+	
		    			"WHERE HOSTNAME=\""+hostname+"\"";
		    	log.debug("Select SQL String "+selectString);
	    		ResultSet rs= selectStmt.executeQuery(selectString);
	    		while (rs.next()){
	    			itExists=true;
	    		} 
	    		rs.close();
	    		selectStmt.close();
	    		c.close();
	    	} catch ( Exception e ) {
	  	      log.error( e.getClass().getName() + ": " + e.getMessage() );
		    }
	    	return itExists;
	    }
	}
}





