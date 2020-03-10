### Version 1.14 and newer

See [GitHub releases](https://github.com/jenkinsci/mock-slave-plugin/releases).

### Version 1.13 (Sep 11 2018)

-   Better support for [Configuration as Code Plugin](https://plugins.jenkins.io/configuration-as-code/).

### Version 1.12 (Mar 13 2018)

-   Remove connection overhead unless pauses were explicitly requested,
    bringing performance in line with local JNLP agents or a simple
    local command launcher.

### Version 1.11 (Oct 10 2017)

-   Enable the plugin to work in a version of Jenkins using a snapshot
    version of Remoting.

### Version 1.10 (Sep 09 2016)

-   Allow the mock cloud to use non-one-shot agents with one executor
    apiece.

### Version 1.9 (May 19 2016)

-   Log launch time of agents.

### Version 1.8 (Aug 04 2015)

-   [JENKINS-25090](https://issues.jenkins-ci.org/browse/JENKINS-25090)
    Fixes related to durable tasks.

### Version 1.6 (Sep 24 2014)

-   Integrating mock cloud with [Durable Task
    Plugin](https://wiki.jenkins.io/display/JENKINS/Durable+Task+Plugin):
    such tasks can now resume on a mock cloud slave, even using the
    one-shot retention strategy.

### Version 1.5 (Sep 17 2014)

-   Mock cloud slaves will now use a “one-shot” retention strategy when
    the number of executors is set to 1.

### Version 1.4 (Feb 19 2014)

-   Added a mock cloud.
-   Maybe added/restored Java 5/6 compatibility.

### Version 1.2/1.3 (Nov 21 2013)

-   Added a whole mock slave as a convenience.
