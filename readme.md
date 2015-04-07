Phabricator-Jenkins Plugin
==========================

This plugin provides [Phabricator][] integration with [Jenkins][]. It allows Jenkins to
report build status and coverage information over Harbormaster (or via comments
if Harbormaster is not enabled).

[Phabricator]: http://phabricator.org/
[Jenkins]: https://jenkins-ci.org/

Configuration
-------------

Navigate to `https://ci.example.com/configure`, replacing ci.example.com with
the URL for your Jenkins instance.

Enter the URL of your Phabricator install, and optionally your [Uberalls][] instance.

![Configure globally](/docs/configure-globally.png)

[Uberalls]: https://github.com/uber/uberalls

Usage
-----

To enable Harbormaster integration, add two string parameters to your jenkins
job: `DIFF_ID` and `PHID`:

![Configure job parameters](/docs/configure-job-parameters.png)

To apply the differential to your workspace before test runs, enable the "Apply
Phabricator Differential" step under "Build Environment":

![Enable build environment](/docs/configure-job-environment.png)

By default, this will reset to the base commit that the differential was made
from. If you wish to apply the patch to master instead, select "Apply patch to master".

To report the build status back to Phabricator after your test run, enable the
"Post to Phabricator" Post-build Action:

![Add post-build action](/docs/configure-job-post-build.png)

If you have [Uberalls][] enabled, enter a path to scan for cobertura reports.

[Uberalls]: https://github.com/uber/uberalls

Harbormaster
------------

Once the plugin is configured, you will want to enable harbormaster via herald
rules to trigger jenkins builds on differentials.

First, create a new Harbormaster build plan with a single step, "Make an HTTP
POST request":

Set the URI to
`https://ci.example.com/buildByToken/buildWithParameters?job=test-example&DIFF_ID=${buildable.diff}&PHID=${target.phid}`,

replacing `https://ci.example.com` with the URI of your Jenkins instance, and
`test-example` with the name of your job. If your Jenkins instance is exposed to
the internet, make sure to install the [Build Token Root Plugin][] and fill in
the `token` parameter.

Set the "When Complete" dropdown to "Wait For Message"

![Harbormaster plan](/docs/harbormaster-plan.png)

[Build Token Root Plugin]: https://wiki.jenkins-ci.org/display/JENKINS/Build+Token+Root+Plugin

Herald
------

Next, create a Global herald rule:

![Herald rule](/docs/herald-rule.png)

Fill in your repository name and build plan.

Test it out
-----------

Try `arc diff`-ing on your repo. If everything goes well, you should see Jenkins
commenting on your diff:

![Example](/docs/uberalls-integration.png)

Development
-----------

Set up your maven file according to
https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial


Testing
-------

Start up Jenkins with the plugin installed
```bash
mvn hpi:run
```

Open your browser to your [local instance](http://localhost:8080/jenkins/)

License
-------

MIT Licensed
