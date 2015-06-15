<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>

<%@ page import="javax.portlet.*"%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet"%>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<portlet:defineObjects />
<jsp:useBean id="roles" type="java.util.List<String>" class="java.util.ArrayList<String>" scope="request"/>
<jsp:useBean id="selectedroles" type="java.util.List<String>" class="java.util.ArrayList<String>" scope="request"/>
<jsp:useBean id="group" class="java.lang.String" scope="request"/>
<jsp:useBean id="host" class="java.lang.String" scope="request"/>
<jsp:useBean id="port" class="java.lang.String" scope="request"/>
<jsp:useBean id="user" class="java.lang.String" scope="request"/>
<jsp:useBean id="password" class="java.lang.String" scope="request"/>
<jsp:useBean id="room" class="java.lang.String" scope="request"/>
<jsp:useBean id="ddns" class="java.lang.String" scope="request"/>
<jsp:useBean id="dnsKeyName" class="java.lang.String" scope="request"/>
<jsp:useBean id="dnsKeyValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="dnsHost" class="java.lang.String" scope="request"/>
<jsp:useBean id="dnsZone" class="java.lang.String" scope="request"/>
<jsp:useBean id="shCmd" class="java.lang.String" scope="request"/>
<jsp:useBean id="shCertDir" class="java.lang.String" scope="request"/>
<jsp:useBean id="shUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="errormessage" class="java.lang.String" scope="request"/>
<jsp:useBean id="ownership" class="java.lang.String" scope="request" />

<liferay-ui:error key="unabletoconnect" message="An error prevented your settings from being saved!" />
<liferay-ui:success key="ok" message="Your settings have been saved!" />
<b>
  myCloud - EDIT MODE
</b>
<br>
<form action="<portlet:actionURL/> " method="POST">
  <liferay-ui:tabs names="Resource Ownership,XMPP Connection,Dynamic DNS,Shell in A Box" refresh="false">

    <liferay-ui:section>

      Group VMs by:
      <br/>
      <br/>
      <table width="100%" cellpadding="3" cellspacing="3">
      <tr>
        <td width="30%">
          <input name="ownership" <%= (ownership.equals("project") ? "checked" : "") %> type="radio" value="project" /> Project
        </td>
        <td width="20%">
          <input name="ownership" <%= (ownership.equals("user") ? "checked" : "") %>  type="radio" value="user" /> User
        </td>
        <td width="50%">
          <input name="ownership" <%= (ownership.equals("roles") ? "checked" : "") %> type="radio" value="roles" /> Roles
        </td>
      </tr>
      <tr>
        <td valign="top">    
          <fieldset  name="project">
            <label for="group">Name:</label>
            <input name="group" type="text" value="<%=group%>"/>
          </fieldset>
        </td>
        <td valign="top">  
          <br>
        </td>
        <td valign="top">
          <fieldset >
            <% for(String s : roles) { %>
            <input type="checkbox" name="selectedroles" value="<%= s %>" <%= (selectedroles.contains(s) == true ? "checked":"")%>/><%= s %> <br> 
            <%}%>
          </fieldset>
        </td>
      </tr>
    </table>
  </liferay-ui:section>
  <liferay-ui:section>
    <table>
      <tr>
        <td><label for="host">Host:</label></td> 
        <td><input name="host" type="text" value="<%=host%>"/></td>
      </tr>
      <tr>
        <td><label for="port">Port:</label></td> 
        <td><input name="port" type="text" value="<%=port%>"/></td>
      </tr>
      <tr>
        <td><label for="user">User:</label></td> 
        <td><input name="user" type="text" value="<%=user%>"/></td>
      </tr>
      <tr>
        <td><label for="password">Password:</label></td> 
        <td><input name="password" type="password" value="<%=password%>"/></td>
      </tr>
      <tr>
        <td><label for="room">Room:</label></td> 
        <td><input name="room" type="room" value="<%=room%>"/></td>
      </tr>
    </table>
  </liferay-ui:section>
  <liferay-ui:section>
    <table>
      <tr>
        <td><label for="ddns">Use DynamicDNS:</label></td> 
        <td>
          <input name="ddns" type="checkbox" value="yes" <%=(ddns.equals("yes")?"checked":"")%> />
        </td>
      </tr>
    </table>

    <fieldset column="true" name="ddnsoptions" >
      <table><tr>
          <td><label for="dns-host">Host:</label></td> 
          <td><input name="dns-host" type="text" value="<%=dnsHost%>"/></td>
        </tr>
        <tr>
          <td><label for="dns-key-name">Key Name:</label></td> 
          <td><input name="dns-key-name" type="text" value="<%=dnsKeyName%>"/></td>
        </tr>   
        <tr>
          <td><label for="dns-key-value">Key Value:</label></td> 
          <td><input name="dns-key-value" type="text" value="<%=dnsKeyValue%>"/></td>
        </tr>
        <tr>
          <td><label for="dns-zone">Zone:</label></td> 
          <td><input name="dns-zone" type="text" value="<%=dnsZone%>"/></td>
        </tr></table>
    </fieldset>

  </liferay-ui:section>
  <liferay-ui:section>      
    <table>
      <tr>
        <td><label for="sh-cmd">Command:</label></td> 
        <td><input name="sh-cmd" type="text" value="<%=shCmd%>"/></td>
      </tr>
      <tr>
        <td><label for="sh-cert-dir">CertDir:</label></td> 
        <td><input name="sh-cert-dir" type="text" value="<%=shCertDir%>"/></td>
      </tr>
      <tr>
        <td><label for="sh-url">Url:</label></td> 
        <td><input name="sh-url" type="text" value="<%=shUrl%>"/></td>
      </tr>
    </table>
  </liferay-ui:section>
</liferay-ui:tabs>
<p></p>
<p class="submit"><input type="submit" name="Submit" value="Save settings"></p>
</form>