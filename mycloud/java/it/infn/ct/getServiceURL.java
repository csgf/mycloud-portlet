/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package it.infn.ct;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.clever.Common.Exceptions.CleverException;
import org.clever.Common.Shared.HostEntityInfo;
import org.clever.Common.VEInfo.VEState;
import org.clever.administration.api.modules.HostAdministrationModule;
import org.clever.administration.api.modules.VMAdministrationModule;

/**
 *
 * @author salvullo
 */
@WebServlet(name = "getServiceURL", urlPatterns = {"/getServiceURL"})
public class getServiceURL extends HttpServlet {

  /**
   * Processes requests for both HTTP
   * <code>GET</code> and
   * <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/html;charset=UTF-8");
    PrintWriter out = response.getWriter();
    
    String IP = null;
    try {

      VMAdministrationModule vam = myCloud4ChainREDS.sf.getSession().getVMAdministrationModule();
      HostAdministrationModule ham = myCloud4ChainREDS.sf.getSession().getHostAdministrationModule();
      
      String name = request.getParameter("name").toLowerCase(Locale.ENGLISH);
      
      for (HostEntityInfo i : ham.listHostManagers()) {
        try {
          List<VEState> vms = vam.listVMs_HOST(i.getNick(), Boolean.FALSE);
          for (VEState vm : vms) {
            if (vm.getName().startsWith(name)) {
              Map<String, Object> details = vam.getVMDetails_HOST(i.getNick(), vm.getName());
              Map<String, String> net = ((List<Map<String, String>>) details.get("network")).get(0);
              IP = net.get("ip");
              break;
            }
          }
        } catch (CleverException ex) {
          Logger.getLogger(getHostByAddr.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    } catch (CleverException ex) {
      Logger.getLogger(myCloud4ChainREDS.class.getName()).log(Level.SEVERE, null, ex);
    }

    if (IP != null) {

      response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      response.setHeader("Location", "http://" + IP);

    } else {
      out.print("<html>");
      out.print("<body>");
      out.print("<h1>Service " + request.getParameter("name") + " not yet deployed! </h1>");
      out.print("</body>");
      out.print("</html>");
    }

    out.close();
  }

  // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
  /**
   * Handles the HTTP
   * <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP
   * <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Short description";
  }// </editor-fold>
}
