=======
MYCLOUD
=======

MyCloud is composed of the following software components:

-	A portlet running on the Liferay instance (Scienze Gateway) : this software represents the interface between the user and clouds. Basically, it performs the user login and authorization phases and provides the GUI in order to interact with clouds (VM managing). 
-	An instance of Clever framework: this component performs the actual operations on the clouds, in particular using the OCCI interface. It receives the requests from the portlet and consequently carries out the OCCI invocations.

The whole portlet architecture can be depicted as follows:

.. image:: images/architecture.png
  :align: center
  :scale: 95%
  :target: https://github.com/csgf/mycloud-portlet

Clever installation
--------------------
Clever framework can be considered as a XMPP remote invocation system. This definition focuses just on an aspect of the framework. Clever framework architecture is shown in the following picture: 

.. image:: images/clever.png
  :align: center
  :scale: 95%
  :target: https://github.com/csgf/mycloud-portlet

Please note that the terms “Host Manager” and “Cluster Manager” are used following the Clever terminology, hence they should not be interpreted literally in this scenario. 

The software pre-requisites for Clever installation are:

- Linux OS. Currently, we are using CentOS6, but almost any distribution could be adopted.
- A Java VM >1.6
- A XMPP server. The implementation adopted is ejabberd server. Only one instance is needed to run Clever. 
- Java VM >= 6. Oracle JDK and IcedTea (Open JDK) were tested.
- Dbus daemon. This software implements IPC functionalities and it is already installed and running on almost any linux distribution installation.
- dbus-launch command. For red-hat like distributions the package is named “dbus-x11”

