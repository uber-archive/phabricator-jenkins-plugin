# Changelog

### 1.8.0 (unreleased)

* Qualify log statements with "phabricator:"
* Send a Harbormaster URI Artifacts for the Jenkins build  (Chris Burroughs)
* Clean up internal Harbormaster API
* Make the Cobertura plugin an optional dependency (only used for Uberalls)
* Consistently set defaults for notifiers (Chris Burroughs)
* Increase unit test coverage to >85%
* Gracefully ignore missing author names/emails from conduit for summary badge
* Report Cobertura coverage data to Harbormaster API
* Add option to preserve formatting in additional comments

### 1.7.2 (2015/08/13)

* Fix HTML escaping on build summary view (regression from auto-escape in 1.7.1)

### 1.7.1 (2015/08/13)

* Fix class loading error in Apache HTTP client
* Bump minimum required Jenkins version to 1.609.2 (from 1.609) so that class exclusions work for above fix
* Add escape-by-default to Jelly templates
* Re-enable Javadoc step

### 1.7 (2015/08/12)

* Conduit token and Phabricator URL are now configured via the [Credentials plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
* Harbormaster messages are now sent over conduit (no more `arc` dependency)
* Removed deprecated "uber-dot-arcanist" functionality
* Removed unused JNA and trove4j dependencies
* Various bugfixes
* Major refactoring and testing

### 1.6.1 (2015/06/15)

* Update wiki path for plugin

### 1.6 (2015/06/14)

* Rename plugin from "Phabricator Plugin" to "Phabricator Differential Plugin"
* Add checkbox to control "Build Started" comments being posted to Phabricator

### 1.5 (2015/06/09)

* Handle invalid responses from conduit
