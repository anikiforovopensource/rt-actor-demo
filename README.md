### rt-actor-demo

This project is a prototype of a Heartbeat Monitoring System, written over the course of three days.
The goal behind the demo was to show how strict reliability and scaling requirements can be met
using a Distributed Actor System.

Because this is a demo prototype, the implementation lacks many bits and pieces, including the
complete absence of any error handling:
 - No exception handling.
 - No Actor supervision.
 - Many protocols are simplified not to deal with errors.

Enjoy browsing or hacking this project, but please remember, this is not meant to be production
quality code.

To launch the demo:
- clone the repo ```git clone git@github.com:PagerDuty/rt-actor-demo```
- cd into the project ```cd rt-actor-demo```
- run using ```sbt run```
