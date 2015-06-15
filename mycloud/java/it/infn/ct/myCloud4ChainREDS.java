package it.infn.ct;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Role;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.RoleServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.portlet.*;
import javax.sql.DataSource;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.clever.Common.Exceptions.CleverException;
import org.clever.Common.Shared.HostEntityInfo;
import org.clever.Common.VEInfo.StorageSettings;
import org.clever.Common.VEInfo.VEDescription;
import org.clever.Common.VEInfo.VEState;
import org.clever.Common.XMPPCommunicator.UUIDProvider;
import org.clever.administration.api.Configuration;
import org.clever.administration.api.modules.HostAdministrationModule;
import org.clever.administration.api.SessionFactory;
import org.clever.administration.api.modules.VMAdministrationModule;
import org.clever.administration.exceptions.CleverClientException;

/**
 * myCloud4ChainREDS Portlet Class
 */
public class myCloud4ChainREDS extends GenericPortlet {

  public static SessionFactory sf = null;
  private static Log log = LogFactoryUtil.getLog(myCloud4ChainREDS.class);
  private DNSManager dns = null;

  public class Result {

    boolean success = false;
    String errnam = null;
    String errmsg = null;
  }

  public class VMInfoEntry {

    public String name = null;
    public String uuid = null;
    public String host = null;
    public boolean deployed = false;
    public boolean running = false;
  }

  public class ServiceInfoEntry {

    public String name = null;
    public List<String> flavors = null;
    public String instanceability = null;
    public String description = null;
  }

  public Map<String, VMInfoEntry> getServiceStatus(Set<String> tags) throws IOException {
    Map<String, VMInfoEntry> result = new HashMap<String, VMInfoEntry>();

    try {

      VMAdministrationModule vam = sf.getSession().getVMAdministrationModule();
      HostAdministrationModule ham = sf.getSession().getHostAdministrationModule();

      for (HostEntityInfo i : ham.listHostManagers()) {
        try {
          List<VEState> vms = vam.listVMs(i.getNick(), tags);
          for (VEState vm : vms) {
            VMInfoEntry vie = result.get(vm.getName());
            vie.deployed = true;
            vie.host = i.getNick();
          }
          vms = vam.listVMs(i.getNick(), tags);
          for (VEState vm : vms) {
            VMInfoEntry sei = result.get(vm.getName());
            sei.running = true;
          }
        } catch (CleverException ex) {
          Logger.getLogger(getHostByAddr.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    } catch (CleverException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }
    return result;
  }

  private Connection getJDBCServices() {
    Connection connection = null;
    try {
      Context ctx = new InitialContext();
      DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/marketplace");
      connection = ds.getConnection();

    } catch (SQLException e) {

      log.error("Error on services DB connection");

    } catch (NamingException e) {
      log.error("NamingException:" + e);
    }
    return connection;
  }

  private boolean configureCleverSession(PortletPreferences pp) {
    Properties p = new Properties();

    for (Entry<String, String[]> e : pp.getMap().entrySet()) {
      if (e.getValue().length == 0) {
        continue;
      }
      p.put(e.getKey(), e.getValue()[0]);
    }

    Configuration configuration = new Configuration();
    try {
      configuration.setSettings(p);
      sf = configuration.buildSessionFactory();

    } catch (CleverClientException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }
    return sf != null;
  }

  private void configureDYNDNS(PortletPreferences pp) {
    if (pp.getValue("ddns", "").equals("yes")) {
      dns = new DNSManager(
          pp.getValue("dns-host", null),
          pp.getValue("dns-key-name", null),
          pp.getValue("dns-key-value", null),
          pp.getValue("dns-zone", null));
    } else {
      dns = null;
    }
  }

  private void configureShellInABox(PortletPreferences pp) {
    Properties conf = new Properties();
    try {

      conf.setProperty("serverCommand", pp.getValue("shellinabox-cmd", "/usr/local/bin/shellinaboxd"));
      conf.setProperty("certDir", pp.getValue("shellinabox-cert-dir", "/etc"));

      Runtime.getRuntime().exec("killall shellinaboxd");
      ShellManager.configure(conf);
    } catch (IOException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response) throws PortletException, IOException {

    PortletPreferences portletPreferences = (PortletPreferences) request.getPreferences();

    if (request.getParameter("ddns") != null) {
      portletPreferences.setValue("ddns", request.getParameter("ddns"));
    } else {
      portletPreferences.setValue("ddns", "no");
    }
    final String[] sr = request.getParameterValues("selectedroles");
    portletPreferences.setValues("selectedroles", (sr != null) ? sr : new String[0]);

    portletPreferences.setValue("ownership", request.getParameter("ownership"));
    portletPreferences.setValue("group", request.getParameter("group"));
    portletPreferences.setValue("xmpp_server", request.getParameter("host"));
    portletPreferences.setValue("xmpp_port", request.getParameter("port"));
    portletPreferences.setValue("xmpp_username", request.getParameter("user"));
    portletPreferences.setValue("xmpp_nickname", request.getParameter("user"));
    portletPreferences.setValue("xmpp_password", request.getParameter("password"));
    portletPreferences.setValue("xmpp_room", request.getParameter("room"));
    portletPreferences.setValue("dns-host", request.getParameter("dns-host"));
    portletPreferences.setValue("dns-zone", request.getParameter("dns-zone"));
    portletPreferences.setValue("dns-key-name", request.getParameter("dns-key-name"));
    portletPreferences.setValue("dns-key-value", request.getParameter("dns-key-value"));
    portletPreferences.setValue("shellinabox-cmd", request.getParameter("sh-cmd"));
    portletPreferences.setValue("shellinabox-cert-dir", request.getParameter("sh-cert-dir"));
    portletPreferences.setValue("shellinabox-url", request.getParameter("sh-url"));
    try {
      if (configureCleverSession(portletPreferences)) {
        configureDYNDNS(portletPreferences);
        configureShellInABox(portletPreferences);
        portletPreferences.store();
        SessionMessages.add(request, "ok");
      } else {
        SessionErrors.add(request, "sessionerror");
      }
    } catch (IOException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }
    response.setPortletMode(PortletMode.EDIT);
  }

  private class isValidVEState implements Predicate<VEState> {

    @Override
    public boolean apply(VEState e) {
      return e.getName() != null;
    }
  }

  public void UpdateDNS(String host, String guestname) throws CleverException {

    VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

    Map<String, Object> details = VMadm.getVMDetails_HOST(host, guestname);
    Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);
    log.info("Updating DNS: " + guestname + " ---> " + net.get("ip"));

    if (dns != null) {
      dns.updateEntry(guestname, net.get("ip"));
    }

  }

  @Override
  public void serveResource(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {

    final String command = (String) request.getParameter("command");
    PortletPreferences portletPreferences = (PortletPreferences) request.getPreferences();

    if (command.equals("listHMs")) {
      List<HostEntityInfo> HMs = new ArrayList<HostEntityInfo>();
      List<Map<String, String>> SiteInfo = null;
      try {
        log.info("listing HMs");
        SiteInfo = Lists.transform(
            sf.getSession().getHostAdministrationModule().listHostManagers(),
            new Function< HostEntityInfo, Map<String, String>>() {

              @Override
              public Map<String, String> apply(HostEntityInfo f) {
                Map<String, String> result = new HashMap<String, String>();
                try {
                  result = (Map<String, String>) (sf.getSession().getHostAdministrationModule().execSyncCommand(f.getNick(),
                      "HyperVisorAgent", "getCloudInfo", new ArrayList(), false));
                  result.put("nick", f.getNick());
                } catch (CleverException ex) {
                  log.error(ex);
                }
                return result;
              }
            });
      } catch (Exception ex) {
        log.error(ex);
      } finally {

        Gson gson = new GsonBuilder().create();

        response.setContentType("application/json");
        response.getPortletOutputStream().write(gson.toJson(SiteInfo).getBytes());

      }

    } else if (command.equals("listVMs")) {
      String hostmgr = (String) request.getParameter("host");
      try {
        Map<String, VMInfoEntry> result = new HashMap<String, VMInfoEntry>();
        try {
          VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

          List<VEState> allvms = VMadm.listVMs(
              hostmgr, this.getAllowedResourceTags(portletPreferences, request));

          for (VEState vm : Iterables.filter(allvms, new isValidVEState())) {
            VMInfoEntry vie = new VMInfoEntry();
            vie.name = vm.getName();
            vie.uuid = vm.getId();
            vie.deployed = true;
            vie.running = vm.getState() == VEState.STATE.RUNNING;
            vie.host = hostmgr;
            result.put(vm.getName(), vie);
          }
        } catch (Exception ex) {
        }
        Gson gson = new GsonBuilder().create();
        response.setContentType("application/json");
        response.getPortletOutputStream().write(gson.toJson(result.values()).getBytes());

      } catch (Exception ex) {
      }
    } else if (command.equals("listServices")) {

      Client client = Client.create();
      final String url
          = "http://" + portletPreferences.getValue("xmpp_server", "localhost")
          + ":8080/market/jaxrs/crud/marketjson/get/" + portletPreferences.getValue("group", "default");
      WebResource resource = client.resource(url);

      final ClientResponse result = resource.accept("application/json").get(ClientResponse.class);

      Gson gson = new GsonBuilder().create();
      if (result.getStatus() != 200) {
        log.error(url);
        log.error(result.getStatus() + result.getEntity(String.class));
      } else {

        List<ServiceInfoEntry> services = gson.fromJson(result.getEntity(String.class),
            new TypeToken<List<ServiceInfoEntry>>() {
            }.getType());
        response.setContentType("application/json");
        response.getPortletOutputStream().write(gson.toJson(services).getBytes());
      }
    } else if (command.equals("DNSupdate")) {
      String name = request.getParameter("name");
      String ip = request.getParameter("ip");
      if (dns != null) {
        dns.updateEntry(name, ip);
        log.info("Updating DNS: " + name + " ---> " + ip);
      }
    } else if (command.equals("DNSremove")) {
      String name = request.getParameter("name");
      log.info("Updating DNS: " + name + " ---> " + "null");
      if (dns != null) {
        dns.removeEntry(name);
      }
    } else if (command.equals("scheduleDeploy")) {

      int instances = Integer.parseInt(request.getParameter("instances"));

      ArrayList<String> result = new ArrayList<String>();

      try {
        List<HostEntityInfo> HMs = sf.getSession().getHostAdministrationModule().listHostManagers();
        UniformIntegerDistribution UID = new UniformIntegerDistribution(0, HMs.size() - 1);

        ArrayList<String> schedule = new ArrayList<String>();

        for (int i : UID.sample(instances)) {
          schedule.add(HMs.get(i).getNick());
        }

        for (HostEntityInfo h : HMs) {
          result.add(
              h.getNick() + "#"
              + Iterables.frequency(schedule, h.getNick()));
        }

      } catch (Exception ex) {
        log.error(ex);
      }
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      response.getPortletOutputStream().write(gson.toJson(result).getBytes());

    } else if (command.equals("deployService")) {

      ArrayList<Result> result = new ArrayList<Result>();

      String host = request.getParameter("host");
      String service = request.getParameter("service").replaceAll("-", "_").toLowerCase();
      String flavor = request.getParameter("flavor");
      String instanceability = request.getParameter("instanceability");

      String uuid = request.getParameter("uuid") == null
          ? Integer.toString(UUIDProvider.getPositiveInteger())
          : request.getParameter("uuid");

      int instances = Integer.parseInt(request.getParameter("instances"));

      List<StorageSettings> ss = Lists.newArrayList(new StorageSettings(0, null, null, "disk0", service));
      VEDescription ved = new VEDescription(ss, null, flavor, null, null, null);

      String guestname = service.replaceAll("_", "-") + "-" + flavor.charAt(0);

      if (instances > 1) {
        try {
          while (instances > 0) {
            Result r = deploy(
                host,
                ved,
                guestname + Integer.toString(UUIDProvider.getPositiveInteger()),
                instanceability.equals("single"),
                this.getAllowedResourceTags(portletPreferences, request));
            instances = instances - 1;
            result.add(r);
            log.info(r);
          }
        } catch (Exception ex) {
          log.error(ex);
        }
      } else {
        Result r = deploy(host, ved, guestname + uuid,
            instanceability.equals("single"),
            this.getAllowedResourceTags(portletPreferences, request));
        result.add(r);
        log.info(r);
      }

      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      response.getPortletOutputStream().write(gson.toJson(result).getBytes());

    } else if (command.equals("serviceExists")) {

      Result result = new Result();

      final String service = request.getParameter("service");
      String host = request.getParameter("host");

      VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();
      try {
        List<String> images = (List<String>) VMadm.execSyncCommand(host, "HyperVisorAgent", "listImageTemplates", new ArrayList(), false);
        if (Iterables.find(images, new Predicate<String>() {
          @Override
          public boolean apply(String t) {
            return service.equals(t);
          }
        }) != null) {
          result.success = true;
        } else {
          result.success = false;
          result.errmsg = "Image " + service + " not found at " + host + "!";
        }
      } catch (CleverException ex) {
        result.success = false;
        result.errmsg = ex.getMessage();
      }
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      response.getPortletOutputStream().write(gson.toJson(result).getBytes());

    } else if (command.equals("undeployService")) {
      try {

        String service = request.getParameter("service");
        String host = request.getParameter("host");

        VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

        VMadm.destroyVM(host, service);

        log.info("Updating DNS: " + service + " --->  null");
        if (dns != null) {
          dns.removeEntry(service);
        }
        ShellManager.getInstance().delShell(service);
      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }
    } else if (command.equals("getServiceStatus")) {
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      response.getPortletOutputStream().write(gson.toJson(getServiceStatus(
          this.getAllowedResourceTags(portletPreferences, request))).getBytes());
    } else if (command.equals("startSH")) {
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      try {
        String h = request.getParameter("host");
        String n = request.getParameter("vm");
        VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

        Map<String, Object> details = VMadm.getVMDetails_HOST(h, n);
        Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);

        ShellManager.getInstance().addShell(n, n, net.get("ip"), 22);
        ShellManager.getInstance().startShellServer();

        String url = portletPreferences.getValue("shellinabox-url", "http://localhost:4200") + "/" + n;
        log.info("Preparing console for " + url);
        response.getPortletOutputStream().write(gson.toJson(url).getBytes());
      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }
    } else if (command.equals("getNoVNCInfo")) {
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");
      try {
        String h = request.getParameter("host");
        String n = request.getParameter("id");
        VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

        //per  ora  cosi'  e' brutto
        Map<String, Object> details = VMadm.getVMDetails_HOST(h, n);
        Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);

        if (details.size() != 1) {
          String display = (String) details.get("display");
          response.getPortletOutputStream().write(gson.toJson(display).getBytes());
        }
      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }
    } else if (command.equals("startService")) {
      String service = request.getParameter("service");
      String host = request.getParameter("host");
      try {
        VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();
        VMadm.startVM_HOST(host, service);

        Map<String, Object> details = VMadm.getVMDetails_HOST(host, service);
        Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);

        log.info("Updating DNS: " + service + " ---> " + net.get("ip"));
        if (dns != null) {
          dns.updateEntry(service, net.get("ip"));
        }

      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }

    } else if (command.equals("stopService")) {
      String service = request.getParameter("service");
      String host = request.getParameter("host");
      try {
        VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();
        VMadm.stopVM_HOST(host, service, false);
        log.info("Updating DNS: " + service + " --->  null");
        if (dns != null) {
          dns.removeEntry(service);
        }
      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }

    } else if (command.equals("getVMinfo")) {
      Gson gson = new GsonBuilder().create();
      response.setContentType("application/json");

      String service = request.getParameter("service");
      String host = request.getParameter("host");

      VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

      Map<String, Object> details;
      try {
        details = VMadm.getVMDetails_HOST(host, service);

        Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);

        String ip = net.get("ip");
        String mem = (String) details.get("memory");
        String cpu = (String) details.get("cores");

        response.getPortletOutputStream().write(gson.toJson(
            Lists.newArrayList(ip, mem, cpu)).getBytes());

      } catch (CleverException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }

  private Result deploy(String host, VEDescription ved, String guestname, boolean monitored, Set<String> tags) {
    Result result = new Result();
    try {

      VMAdministrationModule VMadm = sf.getSession().getVMAdministrationModule();

        VMadm.createVM(host, guestname, ved, monitored, tags);
        Thread.sleep(4000);
      Map<String, Object> details = VMadm.getVMDetails_HOST(host, guestname);
      Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);
      log.info("Updating DNS: " + guestname + " ---> " + net.get("ip"));
      if (dns != null) {
        dns.updateEntry(guestname, net.get("ip"));
      }
        ShellManager.getInstance().addShell(guestname, guestname, net.get("ip"), 22);

      result.success = true;

    } catch (Exception ex) {
      result.errnam = ex.getClass().toString();
      result.errmsg = ex.getMessage();
    } 
    return result;
  }

  @Override
  public void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {

    PortletPreferences portletPreferences = (PortletPreferences) request.getPreferences();

    if (!portletPreferences.getNames().hasMoreElements()
        || sf == null && !this.configureCleverSession(portletPreferences)) {
      SessionErrors.add(request, "notconfiguredyet");
    } else {
      configureShellInABox(portletPreferences);
      configureDYNDNS(portletPreferences);
    }
    response.setContentType("text/html");
    PortletRequestDispatcher dispatcher
        = getPortletContext().getRequestDispatcher("/WEB-INF/jsp/myCloud4ChainREDS_view.jsp");
    dispatcher.include(request, response);
  }

  private Set<String> getAllowedResourceTags(PortletPreferences pp, ResourceRequest request) {
    Set<String> result = null;

    String ownership = pp.getValue("ownership", "project");

    if (ownership.equals("project")) {
      result = Sets.newHashSet(
          pp.getValue("group", "default"));
    } else if (ownership.equals("user")) {
      result = Sets.newHashSet(request.getRemoteUser());
    } else if (ownership.equals("roles")) {
      try {

        Set<String> selectedRoles = Sets.newHashSet(
            pp.getValues("selectedroles", new String[0]));
        List<String> userRoles = Lists.transform(
            RoleServiceUtil.getUserRoles(
                Long.parseLong(request.getRemoteUser())),
            new Function<Role, String>() {

              @Override
              public String apply(Role r) {
                return r.getName();
              }
            });
        result = Sets.newHashSet(Sets.intersection(selectedRoles, Sets.newHashSet(userRoles)));
      } catch (PortalException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      } catch (SystemException ex) {
        Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return result;
  }

  @Override
  public void doEdit(RenderRequest request, RenderResponse response) throws PortletException, IOException {
    try {
      PortletPreferences portletPreferences = (PortletPreferences) request.getPreferences();
      Company company = PortalUtil.getCompany(request);

      List<String> roles = Lists.transform(
          RoleLocalServiceUtil.getRoles(company.getCompanyId()),
          new Function<Role, String>() {

            @Override
            public String apply(Role r) {
              return r.getName();
            }
          });
      response.setContentType("text/html");

      log.error("Roles" + roles.get(0));
      request.setAttribute("roles", roles);
      String[] sr = portletPreferences.getValues("selectedroles", new String[0]) == null
          ? new String[0] : portletPreferences.getValues("selectedroles", new String[0]);

      request.setAttribute("selectedroles", Arrays.asList(sr));
      request.setAttribute("ownership", portletPreferences.getValue("ownership", "project"));
      request.setAttribute("ddns", portletPreferences.getValue("ddns", "no"));
      request.setAttribute("group", portletPreferences.getValue("group", "default"));
      request.setAttribute("host", portletPreferences.getValue("xmpp_server", "localhost"));
      request.setAttribute("port", portletPreferences.getValue("xmpp_port", "5222"));
      request.setAttribute("user", portletPreferences.getValue("xmpp_username", "user"));
      request.setAttribute("password", portletPreferences.getValue("xmpp_password", "password"));
      request.setAttribute("room", portletPreferences.getValue("xmpp_room", "shell@conference.localhost"));
      request.setAttribute("dnsHost", portletPreferences.getValue("dns-host", "localhost"));
      request.setAttribute("dnsZone", portletPreferences.getValue("dns-zone", "zone."));
      request.setAttribute("dnsKeyName", portletPreferences.getValue("dns-key-name", ""));
      request.setAttribute("dnsKeyValue", portletPreferences.getValue("dns-key-value", ""));
      request.setAttribute("shCmd", portletPreferences.getValue("shellinabox-cmd", "/usr/local/bin/shellinaboxd"));
      request.setAttribute("shCertDir", portletPreferences.getValue("shellinabox-cert-dir", "/etc"));
      request.setAttribute("shUrl", portletPreferences.getValue("shellinabox-url", "http://localhost:4200"));
      PortletRequestDispatcher dispatcher
          = getPortletContext().getRequestDispatcher("/WEB-INF/jsp/myCloud4ChainREDS_edit.jsp");
      dispatcher.include(request, response);
    } catch (PortalException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    } catch (SystemException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
