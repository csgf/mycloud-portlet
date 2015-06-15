<%@page import="com.liferay.portal.kernel.servlet.SessionErrors"%>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>


<%@ page import="javax.portlet.*"%>
<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet"%>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>

<liferay-ui:error key="notconfiguredyet" message="Your portlet has not been configured yet!" />

<portlet:defineObjects />


<% if (SessionErrors.isEmpty(renderRequest)) {%>
<script type="text/javascript">

  var jQ = jQuery.noConflict(true);

  jQ.fn.exists = function() {
    return this.length !== 0;
  }

  jQ.styleSheetContains = function(f) {
    var hasstyle = false;
    var fullstylesheets = document.styleSheets;
    for (var sx = 0; sx < fullstylesheets.length; sx++) {
      var sheetclasses = fullstylesheets[sx].rules || document.styleSheets[sx].cssRules;
      for (var cx = 0; cx < sheetclasses.length; cx++) {
        if (sheetclasses[cx].selectorText == f) {
          hasstyle = true;
          break;
        }
      }
    }
    return hasstyle;
  };
  var timer;
  var interval = 10000; //30 sec
  var timerStarted = false;

  function startAutoRefresh() {
    if (!timerStarted) {
      timer = setInterval("refresh()", interval);
      timerStarted = true;
    }
  }

  function stopAutoRefresh() {
    if (timerStarted) {
      clearInterval(timer);
      timerStarted = false;
    }
  }

  function refresh() {
    alert("timer");
  }

  function createHMNode(siteinfo) {

    // clone the "placeholder"
    var hmname = siteinfo.nick;
    jQ('#hm').clone().attr('id', 'hm-' + hmname).appendTo("#hms").
            removeClass('ui-helper-hidden').
            draggable({
              handle: "div.drag-handle",
              containment: "#cloud-status",
              scroll: false
            }).find("p").html(hmname);

    var stack = siteinfo.cloudManager;
    var site = siteinfo.organization;

    jQ("[id='hm-" + hmname + "']").find("div.hm-footer").
            addClass("ui-logo-site-footer-" + site);

    jQ("[id='hm-" + hmname + "']").find("div.ui-logo-stack").
            addClass("ui-logo-stack-" + stack);

    jQ("[id='hm-" + hmname + "']").find(".drag-handle").
            find(".refresh-icon").click(
            function() {
              refreshVMsInfo(hmname);
            }
    );

    // make it droppable
    jQ("[id='hm-" + hmname + "']").find("div.drop-target").droppable({
      accept: "[id^='srv-'], li.deployed-vm",
      activeClass: "ui-state-hover",
      hoverClass: "ui-state-active",
      drop: function(event, ui) {

        var vms = jQ('li.deployed-vm', ui.helper);

        if (jQ(vms).exists()) { // Dropped some VM for being moved

          var hm_to = jQ(this).parent().find('p').html();

          jQ(vms).each(function() {

            var srvname = jQ(this).find('span.name').html();
            var hm_from = jQ('#' + srvname).parents('div.node').find('p').html();
            if (hm_from != hm_to) {
              showOverlay(jQ("[id='hm-" + hm_from + "']").find(".drop-target"));
              showOverlay(jQ("[id='hm-" + hm_to + "']").find(".drop-target"));

              console.log("Moving " + srvname + " from " + hm_from + " to " + hm_to);

              undeployService({"host": hm_from, "service": srvname}, {
                success: function() {
                  refreshVMsInfo(hm_from);
                },
                failure: function() {
                }
              });
              var info = /^(.+)-([mslx])([0-9]+)(.*)$/.exec(srvname);
              deployService(jQ.param({
                flavor: ({"s": "small", "m": "medium", "l": "large", "x": "xlarge"})[info[2]],
                service: info[1],
                host: hm_to,
                instances: 1,
                uuid: info[3],
                istanceability: jQ('#' + srvname).hasClass('multi') ? "multi" : "single"
              }), {
                success: function() {
                  refreshVMsInfo(hm_to);
                },
                failure: function() {
                }
              });
            }
          });
        }
        else { // Dropped a service for being deployed

          var srvname = jQ(ui.draggable).find('p').html();
          var hmname = jQ(this).parent().find('p').html();

          console.log("dropped " + srvname + " on " + hmname);

          openDeployDialog({
            flavors: jQ(ui.draggable).find('ul.flavors li'),
            service: srvname,
            host: hmname
          });
        }

      }
    });
  }

  function refreshHMsInfo() {
    showOverlay(jQ("#cloud-status"));
    jQ("#hms").empty();
    jQ.getJSON('<portlet:resourceURL><portlet:param name="command" value="listHMs"/></portlet:resourceURL>',
            function(response) {
              hideOverlay(jQ("#cloud-status"));
              var hmcount = 0;
              for (var i in response) {
                var hmname = response[i].nick;
                if (jQ("[id='hm-" + hmname + "']").size() == 0) {
                  createHMNode(response[i]);
                  hmcount++;
                }
                refreshVMsInfo(hmname);
              }
              if (hmcount == 0)
                jQ("#no_hm_found_message").addClass("portlet-msg-error");
              else
                jQ("#no_hm_found_message").removeClass("portlet-msg-error");
            });

  }
  function custom_drag_helper() {

    var selected = jQ('#hms input:checked').parents('li');
    if (selected.length === 0) {
      selected = jQ(this);
    }
    var container = jQ('<div/>').attr('id', 'dragging-group');
    container.append(selected.clone());
    return container;

  }

  function compareVMName(a, b) {
    return a.name > b.name;
  }

  function refreshVMsInfo(hmname) {

    var url = '<portlet:resourceURL><portlet:param name="command" value="listVMs"/></portlet:resourceURL>';

    showOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));
    jQ.getJSON(url + '&host=' + hmname, function(response) {

      hideOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));
      jQ("[id='hm-" + hmname + "']").find('ul').empty();

      response.sort(compareVMName);

      for (var i in response) {

        var vmname = response[i].name;
        var srvname = /^(.+)-([mslx])([0-9]+)(.*)$/.exec(vmname)[1];

        jQ('#srv-' + srvname + '.single').remove();

        var imgsrc = "<%=renderRequest.getContextPath()%>/images/button-" +
                (response[i].running == true ? "green" : "red") + ".png";

        jQ('<li>', {"id": vmname}).addClass('deployed-vm').wrapInner(jQ('<span>', {html: vmname}).addClass('name')).
                append(jQ('<span>').addClass('controls')).
                prepend(jQ('<img>', {'src': imgsrc}).addClass("status")).
                prepend(jQ('<input>', {'type': 'checkbox'}).addClass('action-check')).
                appendTo(jQ("[id='hm-" + hmname + "']").find('ul')).draggable({
          revert: false,
          opacity: 0.5,
          helper: custom_drag_helper
        });

        jQ("li[id='" + vmname + "']").find('span.controls').empty();

        if (response[i].running == true) {

          jQ('<img>', {'src': "<%=renderRequest.getContextPath()%>/images/power_off.png",
            'alt': "Click to Power Off"
          }).addClass('power-off').appendTo(jQ("li[id='" + vmname + "']").find('span.controls'));

          jQ('<img>', {'src': "<%=renderRequest.getContextPath()%>/images/console.png",
            'alt': "Click to open Console window on " + vmname
          }).addClass('console').appendTo(jQ("li[id='" + vmname + "'] span.controls"));

          //new!
          //TODO provvisorio
          if (vmname.indexOf("myCloudStation") == -1) //doesn't start with
            jQ('<img>', {'src': "<%=renderRequest.getContextPath()%>/images/netinfo3.png",
              'alt': "Click to get network information of " + vmname
            }).addClass('netinfo').appendTo(jQ("li[id='" + vmname + "'] span.controls"));
          ///////

        } else {
          jQ('<img>', {'src': "<%=renderRequest.getContextPath()%>/images/power_on.png",
            'alt': "Click to Power On"
          }).addClass('power-on').appendTo(jQ("li[id='" + vmname + "']").find('span.controls'));
        }
        jQ('#srv-' + vmname).remove();
      }//close for

      // binding click for console img
      bindConsoleControls();

      // binding click for power-off img
      bindPoweroffControls();

      // binding click for power-on img
      bindPoweronControls();

      bindNetworkInfoControls();

    });//close ajax handler function
  }

  function bindPoweronControls() {
    jQ('li span.controls img.power-on').each(function() {
      var id = jQ(this).parent().parent().attr('id');
      var hm = jQ(this).parents('div.node').find('p').html();
      jQ(this).unbind("click");
      jQ(this).click(function() {
        //showOverlay(jQ(this).parents(".drop-target"));
        showOverlay(jQ("#cloud-status"));
        showOverlay(jQ("#service-status"));
        startService({"host": hm, "service": id}, {
          success: function() {
            //hideOverlay(jQ(this).parents(".drop-target"));
            hideOverlay(jQ("#cloud-status"));
            hideOverlay(jQ("#service-status"));
            refreshVMsInfo(hm);
          },
          failure: function() {
            //hideOverlay(jQ(this).parents(".drop-target"));
            hideOverlay(jQ("#cloud-status"));
            hideOverlay(jQ("#service-status"));
          }
        });
      });
    });
  }

  function bindPoweroffControls() {
    jQ('li span.controls img.power-off').each(function() {
      var id = jQ(this).parent().parent().attr('id');
      var hm = jQ(this).parents('div.node').find('p').html();
      jQ(this).unbind("click");
      jQ(this).click(function() {
        showOverlay(jQ("#cloud-status"));
        showOverlay(jQ("#service-status"));
        //showOverlay(jQ(this).parents(".drop-target"));
        stopService({"host": hm, "service": id}, {
          success: function() {
            hideOverlay(jQ("#cloud-status"));
            hideOverlay(jQ("#service-status"));
            //hideOverlay(jQ(this).parents(".drop-target"));
            refreshVMsInfo(hm);
          },
          failure: function() {
            hideOverlay(jQ("#cloud-status"));
            hideOverlay(jQ("#service-status"));
            //hideOverlay(jQ(this).parents(".drop-target"));
          }
        });
      })
    });
  }

  function bindNetworkInfoControls() {
    jQ('li span.controls img.netinfo').each(function() {
      var id = jQ(this).parent().parent().attr('id');
      var hm = jQ(this).parents('div.node').find('p').html();
      jQ(this).unbind("click");
      jQ(this).click(function() {
        getVMinfo(hm, id);
      });
    });
  }

  function bindConsoleControls() {
    jQ('li span.controls img.console').each(function() {
      var vm = jQ(this).parent().parent().attr('id');
      var hm = jQ(this).parents('div.node').find('p').html();
      jQ(this).unbind("click");
      jQ(this).click(
              function() {

                //showOverlay(jQ(this).parents(".drop-target"));
                //showOverlay(jQ("#cloud-status"));
                //showOverlay(jQ("#service-status"));
                jQ.getJSON('<portlet:resourceURL><portlet:param name="command" value="startSH"/></portlet:resourceURL>&host=' + hm + "&vm=" + vm,
                        function(url) {

                          jQ("#noVNC-dialog-template").clone().
                                  prependTo("#mycontainer").
                                  removeAttr("id").
                                  removeClass("ui-helper-hidden").
                                  append(
                                          jQ("<iframe />").attr("src", url)
                                          ).dialog({
                            width: 770,
                            height: 520,
                            resizable: false,
                            title: "SSH session with " + vm + " on " + hm
                          });
                        });

                //        jQ.getJSON('<portlet:resourceURL><portlet:param name="command" value="getNoVNCInfo"/></portlet:resourceURL>&host='+hm+"&id="+id, 
                //        function(display){
                //          //hideOverlay(jQ(this).parents(".drop-target"));
                //          hideOverlay(jQ("#cloud-status"));
                //          hideOverlay(jQ("#service-status"));
                //          
                //          console.log("Opening console "+ display);
                //          
                //          jQ("#noVNC-dialog-template").clone().
                //            prependTo("#mycontainer").
                //            removeAttr("id").
                //            removeClass("ui-helper-hidden").
                //            append(
                //          jQ("<iframe />").
                //            attr("src", display)
                //        ).
                //            dialog({width:770 , height:520, resizable:false, title: "noVNC session with "+id+" on "+hm});
                //                
                //        }
                //      );
              }
      );//click()
    });
  }
  function openDeployDialog(o) {

    var dlg = jQ("div.deploy-dialog-template").clone().
            prependTo("#mycontainer").removeClass("deploy-dialog-template");

    var select = jQ(dlg).find('select[name="flavor"]');
    var instances = jQ(dlg).find('input[name="instances"]');
    var service = jQ(dlg).find('input[name="service"]');
    var host = jQ(dlg).find('input[name="host"]');
    var instanceability = jQ(dlg).find('input[name="instanceability"]');

    jQ(service).attr('value', o.service);
    jQ(instanceability).attr('value', o.instanceability);
    jQ(host).attr('value', o.host);
    jQ.each(o.flavors, function() {
      jQ(select).append('<option value="' + jQ(this).html() + '">' + jQ(this).html() + '</option>');
    });
    jQ(instances).spinner({min: 1, max: 99});
    jQ(dlg).removeClass("ui-helper-hidden").
            dialog({
              resizable: false,
              modal: true,
              buttons: {
                "Deploy": function() {

                  var hmname = jQ(this).find('input[name="host"]').attr('value');
                  if (hmname == "auto") {
                    var url = '<portlet:resourceURL><portlet:param name="command" value="scheduleDeploy"/></portlet:resourceURL>&instances=' + instances.attr("value");
                    console.log(url);

                    jQ.getJSON(url, function(result) {
                      for (i in result) {
                        var frm = dlg.clone();
                        var host = /(.*)#(.*)/.exec(result[i])[1];
                        var n = /(.*)#(.*)/.exec(result[i])[2];
                        jQ('input[name="host"]', frm).attr("value", host);
                        jQ('input[name="instances"]', frm).attr("value", n);
                        jQ('se[name="select"]', frm).attr("value", select);
                        if (n > 0) {
                          showOverlay(jQ("[id='hm-" + host + "']").find(".drop-target"));
                          deployService(frm.serialize(), {
                            success: function() {
                            },
                            failure: function() {
                            }
                          });
                        }
                      }
                      ;
                    });
                  } else {
                    showOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));
                    var service = jQ(this).find('form[name="deploy"]').find('input[name="service"]').attr("value");
                    var instanceability = jQ(this).find('form[name="deploy"]').find('input[name="instanceability"]');
                    jQ(instanceability).attr('value', jQ('#srv-' + service).hasClass('multi') ? "multi" : "single");
                    console.log("instanceability = " + jQ(instanceability).attr('value'));
                    deployService(jQ(this).find('form[name="deploy"]').serialize(), {
                      success: function() {
                      },
                      failure: function() {
                      }
                    });
                  }
                  jQ(this).dialog("close");
                  jQ(this).remove();
                },
                Cancel: function() {
                  jQ(this).dialog("close");
                  jQ(this).remove();
                }
              }
            });

  }


  function createServiceNode(info) {
    var srv = jQ('#srv').clone().attr('id', 'srv-' + info.name.toLowerCase()).appendTo("#services").
            removeClass('ui-helper-hidden').
            addClass(info.instanceability).draggable({
      handle: ".drag-handle",
      revert: true
    });

    if (jQ.styleSheetContains(".ui-logo-service-" + info.name) == true) {
      jQ(srv).find(".ui-logo-service").
              addClass("ui-logo-service-" + info.name);
    }
    else {
      jQ(srv).find("p").css("display", "block");
    }
    jQ(srv).find("p").html(info.name);
    jQ(srv).attr("title", info.description);

    var ul = jQ(srv).find('ul.flavors');

    jQ.each(info.flavors, function(i, v) {

      jQ(ul).append('<li>' + v + '</li>');
    });
    jQ(srv).find("button").button({
      icons: {
        primary: 'ui-icon-cloud-upload'
      },
      text: false
    }).bind('click', function() {
      openDeployDialog({
        flavors: jQ(this).parent().find('ul.flavors li'),
        service: info.name,
        host: "auto",
        instanceability: jQ("#srv-" + info.name).hasClass('multi') ? "multi" : "single"
      });
    });
    //TODO soluzione provvisoria per la gestione dei guest
    if (name == "myCloudStation") {
      jQ("#srv-" + name + " img").attr("src", "<%=renderRequest.getContextPath()%>/images/guest.png");
    }
  }

  function refreshServicesInfo() {
    showOverlay(jQ("#service-status"));
    jQ.getJSON('<portlet:resourceURL><portlet:param name="command" value="listServices"/></portlet:resourceURL>', function(response) {
      hideOverlay(jQ("#service-status"));
      jQ("#services").empty();
      var srvcount = 0;

      for (var i in response) {
        createServiceNode(response[i]);
        srvcount++;

      }
      if (srvcount == 0)
        jQ("#no_srv_found_message").addClass("portlet-msg-error");
      else
        jQ("#no_srv_found_message").removeClass("portlet-msg-error");
    });
  }

  function deployService(options, callbacks) {

    var url = '<portlet:resourceURL><portlet:param name="command" value="deployService"/></portlet:resourceURL>&' + options;
    console.log(url);
    jQ.getJSON(url, function(result) {

      var hmname = options.host;
      var status = result[0].success;

      console.log(result[0]);

      if (!status) {
         hideOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));
        if(result[0].errmsg.search(/not found/i) >=0) {
          alert("The requested resource is not available on the selected cloud!");
        }
        else {
            alert(result[0].errmsg);
        }
      }
      else {
        showOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));
        window.setTimeout(function() {
          refreshVMsInfo(hmname);
        }, 10000);
      }
    });
    //    jQ.ajax({"url" : url}).success(
    //    window.setTimeout(function(){callbacks.success();},25000)//25000
    //  );
  }
  function undeployService(options, callbacks) {
    var url = '<portlet:resourceURL><portlet:param name="command" value="undeployService"/></portlet:resourceURL>&' + jQ.param(options);
    jQ.ajax({"url": url}).success(
            window.setTimeout(function() {
              callbacks.success();
            }, 10000)//10000
            );
  }
  function startService(options, callbacks) {
    var url = '<portlet:resourceURL><portlet:param name="command" value="startService"/></portlet:resourceURL>&' + jQ.param(options);
    jQ.ajax({"url": url}).success(
            window.setTimeout(function() {
              callbacks.success();
            }, 10000)//10000
            );
  }
  function stopService(options, callbacks) {
    var url = '<portlet:resourceURL><portlet:param name="command" value="stopService"/></portlet:resourceURL>&' + jQ.param(options);
    jQ.ajax({"url": url}).success(
            window.setTimeout(function() {
              callbacks.success();
            }, 10000)//10000
            );
  }
  function getVMinfo(host, service) {
    //var url = '<portlet:resourceURL><portlet:param name="command" value="getVMinfo"/></portlet:resourceURL>&host='+host+'&service='+service;
    jQ.getJSON('<portlet:resourceURL><portlet:param name="command" value="getVMinfo"/></portlet:resourceURL>&host=' + host + '&service=' + service,
            function(info) {

              jQ("#VMinfo-dialog-template").clone().
                      prependTo("#mycontainer").
                      attr("id", "VMinfo").
                      removeClass("ui-helper-hidden").
                      dialog({
                        width: 400,
                        height: 150,
                        resizable: false,
                        title: "VM info for " + service,
                        modal: true
                      });
              if (service.match(/^generic-www*/) != null) {
                jQ("#VMinfo span#dn").html("<a href='http://" + service + ".chain-project.eu' target=blank>" + service + ".chain-project.eu</a>");
              } else {
                jQ("#VMinfo span#dn").html(service + ".chain-project.eu");
              }
              jQ("#VMinfo span#ip").html(info[0]);
              jQ("#VMinfo span#mem").html(info[1]);
              jQ("#VMinfo span#cpu").html(info[2]);
            }
    );
  }

  function showOverlay(element) {

    jQ("#waiting-overlay-template").clone().removeAttr("id").prependTo(element);
    element.find(".waiting-overlay").width(element.width());
    element.find(".waiting-overlay").height(element.height());
    element.find(".waiting-overlay").css({
      "background-position": "center center",
      "margin-top": "0",
      "padding-top": "0"
    });
    element.find(".waiting-overlay").removeClass("ui-helper-hidden");

  }

  function hideOverlay(element) {
    element.find(".waiting-overlay").remove();
  }

  function showDeployHelper() {
    jQ("#deploy-helper").removeClass("ui-helper-hidden");
  }

  function hideDeployHelper() {
    jQ("#deploy-helper").addClass("ui-helper-hidden");
  }

  function showCloudHelp() {
    var dlg = jQ("#help-dialog-template").clone().
            prependTo("#mycontainer").
            removeAttr("id").
            removeClass("ui-helper-hidden").
            dialog({
              width: 650,
              height: 550,
              resizable: false,
              title: "MyCloud Help",
              modal: true
            });
    jQ('a.media', dlg).media({width: 600, height: 450});
  }
  function refreshAllInfo() {
    refreshServicesInfo();
    refreshHMsInfo();
  }
  //MAIN of SCRIPT
  jQ(function() {
    //getNetworkInfo("infn-wn-51.ct.infn.it", "AGLRTool");

    jQ('body').scrollTop(0);
    refreshServicesInfo();
    refreshHMsInfo();

    //startAutoRefresh();

    jQ('#trash-basket').droppable({
      accept: "li.deployed-vm",
      activeClass: "ui-state-hover",
      hoverClass: "ui-state-active",
      drop: function(event, ui) {

        jQ('li', jQ(ui.helper)).each(function() {

          var srvname = jQ(this).find('span.name').html();
          var hmname = jQ('#' + srvname).parents('div.node').find('p').html();

          console.log("Undeploying " + srvname + " from " + hmname);

          showOverlay(jQ("[id='hm-" + hmname + "']").find(".drop-target"));

          undeployService({"host": hmname, "service": srvname}, {
            success: function() {
              refreshVMsInfo(hmname);
            },
            failure: function() {
            }
          });
        });
      }
    });

    jQ('#multiupload-basket').droppable({
      accept: "[id^='srv-']",
      activeClass: "ui-state-hover",
      hoverClass: "ui-state-active",
      drop: function(event, ui) {

        var srvname = jQ(ui.draggable).find('p').html();

        openDeployDialog({
          flavors: jQ(ui.draggable).find('ul.flavors li'),
          service: srvname,
          host: 'auto'
        });
      }
    });


    jQ(document).tooltip();
    jQ('#cloud-status').slimScroll({
      railVisible: true,
      alwaysVisible: true,
      height: '498px',
      width: '100%'
    });
    jQ('#selectAll').click(function() {
      jQ('#hms input').attr('checked', 'checked');
      return false;
    });

    jQ('#selectNone').click(function() {
      jQ('#hms input').removeAttr('checked');
      return false;
    });

    jQ('#selectInvert').click(function() {
      jQ('#hms input').each(function() {

        if (jQ(this).attr('checked')) {
          jQ(this).removeAttr('checked');
        }
        else {
          jQ(this).attr('checked', 'checked');
        }
      });
      return false;
    });
  });
  </script>

  <div id="mycontainer">

    <div class="deploy-dialog-template ui-helper-hidden" title="Upload to Cloud...">  
      <p><span class="ui-icon ui-icon-alert" style="float: left; margin: 0 7px 20px 0;"></span>
        Select the flavor and the number of instances to deploy.</p>
      <form name="deploy">
        <p>
          <label for="flavors">Flavor:</label><select name="flavor"></select>
        </p>
        <p>
          <label for="spinner">Instances:</label><input size="2" value="1" name="instances" />
        </p>
        <input type="hidden" name="instanceability" value="multi"/>
        <input type="hidden" name="service"/>
        <input type="hidden" name="service"/>
        <input type="hidden" name="host" value="auto"/>

      </form>
    </div>
    <div id="noVNC-dialog-template" class="noVNC-dialog ui-helper-hidden">
    </div>
    <div id="VMinfo-dialog-template" class="VMinfo-dialog ui-helper-hidden">
      <p><strong>Full Name   </strong><span id="dn"></span></p>
      <p><strong>IP          </strong><span id="ip"></span></p>
      <p><strong>Memory      </strong><span id="mem"></span></p>
      <p><strong>Cores       </strong><span id="cpu"></span></p>
    </div>
    <div id="help-dialog-template" class="help-dialog ui-helper-hidden">
      <a class="media" href="<%=renderRequest.getContextPath()%>/media/help.pdf"></a>
  </div>

  <div id="waiting-overlay-template" class="loading-animation waiting-overlay ui-helper-hidden">
  </div>

  <div id="hm" class="node draggable ui-helper-hidden">
    <div class="drag-handle ui-widget-header ui-helper-clearfix ui-corner-top hm-header">
      <div class="ui-logo">
        <div class="ui-logo-stack"></div>
        <div style="display:block;float:right;">
          <img class="refresh-icon" src="<%=renderRequest.getContextPath()%>/images/refresh.png"/>
        </div>
      </div>
      <p style="display:none;"></p>
    </div>
    <div class="drop-target ui-widget-content hmstatus">
      <ul style="margin : none"></ul>
      <img class="loading ui-helper-hidden" src="<%=renderRequest.getContextPath()%>/images/loading.gif"/>
    </div>
    <div class="hm-footer ui-widget-header ui-corner-bottom ui-logo-site-footer"></div>
  </div>

  <div id="srv" class="service draggable ui-helper-hidden ui-widget-header ui-draggable">
    <div style="display:block;float:left" class="drag-handle">
      <img  src="<%=renderRequest.getContextPath()%>/images/server.png"/>
      <div class="ui-logo-service"></div>
      <p style="display:none;"></p>
    </div>
    <div style="display:none;">
      <ul class="flavors"></ul>
    </div>
    <!--    <button class="deploy"></button>-->
  </div>


  <div id="cloud-container">
    <div class="container-header ui-logo-stack-occi">
      <p class="ui-widget"><strong>OCCI-accessed cloud(s) orchestrated by CLEVER</strong></p>
      <img src="<%=renderRequest.getContextPath()%>/images/help.png" onclick="showCloudHelp()"/>
      <img class="refresh-icon" src="<%=renderRequest.getContextPath()%>/images/refresh.png" onclick="refreshAllInfo()"/>
      <div id="selectActions">
        <span>Select:</span>
        <ul>
          <li><a id="selectAll" href="#">all</a></li>
          <li><a id="selectNone" href="#">none</a></li>
          <li><a id="selectInvert" href="#">invert</a></li>
        </ul>
      </div>
    </div>    
    <div id="cloud-status" class="ui-widget ui-widget-content ui-corner-left">
      <div id="no_hm_found_message" class="ui-helper-hidden" >No resources available in your cloud</div>
      <div id="hms">
      </div>
    </div>
  </div>  
  <div id="services-container">   
    <div class="container-header-services">
      <p class="ui-widget "><strong>Service(s)</strong></p>
    </div>
    <div id="service-status" class="ui-widget ui-widget-content ui-corner-right">
      <div id="no_srv_found_message" class="ui-helper-hidden" >No services available in your cloud</div>
      <div id="services"></div>
    </div>
    <div class="ui-widget ui-widget-content ui-corner-right">
      <div id="trash-basket" >
        <img src="<%=renderRequest.getContextPath()%>/images/trash.png"/>
      </div>
      <div id="multiupload-basket" >
        <img src="<%=renderRequest.getContextPath()%>/images/multi-upload.png"/>
      </div>
    </div>

  </div>
</div>

<% } %>
