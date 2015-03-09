Project description
============

Jetty is a lightweight highly scalable java based web server and servlet engine.
Our goal is to support web protocols like HTTP, HTTP/2 and WebSocket in a high
volume low latency way that provides maximum performance while retaining the ease
of use and compatibility with years of servlet development. Jetty is a modern
fully async web server that has a long history as a component oriented technology
easily embedded into applications while still offering a solid traditional
distribution for webapp deployment.

- [https://projects.eclipse.org/projects/rt.jetty](https://projects.eclipse.org/projects/rt.jetty)

This fork adds enhancements to Jetty to support auto-scaling in container engines like OpenShift or Docker. Specifically flexible network binding mechanism that enables Jetty services to listen on IP/Port dynamically allocated by the container engine.

