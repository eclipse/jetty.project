# DO NOT EDIT - See: https://www.eclipse.org/jetty/documentation/current/startup-modules.html

[description]
Demo Jetty Webapp

[tags]
demo
webapp

[depends]
deploy
jdbc
jsp
jstl
annotations
ext
servlets
websocket-javax
websocket-jetty
demo-realm

[files]
webapps/demo-jetty.d/
basehome:modules/demo.d/demo-jetty.xml|webapps/demo-jetty.xml
maven://org.eclipse.jetty/test-jetty-webapp/${jetty.version}/war|webapps/demo-jetty.war
basehome:modules/demo.d/demo-jetty-override-web.xml|webapps/demo-jetty.d/demo-jetty-override-web.xml
