# Phabricator-Jenkins Plugin [![Build Status](https://travis-ci.org/uber/phabricator-jenkins-plugin.svg?branch=master)](https://travis-ci.org/uber/phabricator-jenkins-plugin) [![Coverage Status](https://coveralls.io/repos/uber/phabricator-jenkins-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/uber/phabricator-jenkins-plugin?branch=master)

This plugin provides [Phabricator][] integration with [Jenkins][]. It allows Jenkins to
report build status and coverage information over Harbormaster (or via comments
if Harbormaster is not enabled).

[Phabricator]: http://phabricator.org/
[Jenkins]: https://jenkins-ci.org/

Table of Contents
=================
* [Requirements](#requirements)
* [Configuration](#configuration)
  * [Phabricator Configuration](#phabricator-configuration)
  * [Jenkins Setup](#jenkins-setup)
* [Usage](#usage)
  * [Jenkins Job](#jenkins-job)
  * [Harbormaster](#harbormaster)
  * [Herald](#herald)
* [Advanced Usage](docs/advanced.md)
* [Test Your Configuration](#test-your-configuration)
* [Development](#development)
* [Testing](#testing)


Requirements
=============

* [Arcanist](https://github.com/phacility/arcanist) is installed on the Jenkins nodes where the tests will be run (e.g. not just the master) the `arc` binary is in `$PATH` or configured explicitly in the global settings.

Configuration
=============

Before the plugin can be used, a few configuration steps on your
Phabricator and Jenkins instances need to be completed.

Phabricator Configuration
-------------------------

In this section, you'll create a bot user in Phabricator and generate a Conduit API token. If you already have a bot user and a Conduit API token, skip to the "Jenkins Setup Section".

1. Create a bot user in Phabricator.
2. Click **Edit Settings** on the manage page for that user
3. Click **Conduit API Tokens** on the left of the settings page
4. Click **Generate API Token**, and accept.
5. Copy the token.

Jenkins Setup
-------------

1. Navigate to `https://ci.example.com/configure` with your base Jenkins URL in place of "ci.example.com".
2. Navigate to the **Phabricator** section and click the **Add** button. ![Add Credentials](/docs/add-credentials.png)
3. From the **Kind** dropdown, select **Phabricator Conduit Key**.
4. Enter the base URL for your Phabricator instance in the **Phabricator URL** field. For example `https://phabricator.example.com`.
5. Enter a description in the **Description** field for readability.![Configure Credentials](/docs/configure-credentials.png)
6. Paste the Conduit API token (created in the Phabricator Configuration section) in the **Conduit Token** field.
7. Click the **Add** button.
8. Click the **Save** button.

Usage
=====

Now that Jenkins and Phabricator are configured you can configure your Jenkins job and Harbormaster.

Jenkins Job
-----------

1. Navigate to the Jenkins job you want to integrate with Phabricator.
2. Click the **Configure** button.
3. Click the **Add Parameter** button and select **String Parameter**.
4. Enter `DIFF_ID` in the **Name** field of the parameter.
5. Repeat step 3.
6. Enter `PHID` in the **Name** field of the second parameter. ![Configure job parameters](/docs/configure-job-parameters.png)
7. If you want to apply the differential to your workspace before each test run, navigate to the **Build Environment** section and select the **Apply Phabricator Differential** checkbox. This resets to the base commit the differential was generated from. If you'd rather apply the patch to master, select the **Apply patch to master** checkbox.
![Enable build environment](/docs/configure-job-environment.png)
8. To report the build status to Phabricator after the test completes:
  1. Navigate to the **Post-build Actions** section.
  2. Click the **Add post-build action** button and select **Post to Phabricator**.
  3. Make sure the **Comment on Success** and **Comment with console link on Failure** checkboxes are selected.
  4. Optionally:
    1. If you have [Uberalls](https://github.com/uber/uberalls) enabled, enter a path to scan for Coverage reports.
    2. If you want to post additional text to Phabricator other than "Pass" and "Fail", select the **Add Custom Comment** checkbox. Then create a `.phabricator-comment` file and enter the text you want Jenkins to add to the build status comment in Phabricator.
![Add post-build action](/docs/configure-job-post-build.png)

Harbormaster
------------

With Phabricator, Jenkins, and your Jenkins jobs configured it's time to configure a new Harbormaster build plan. This build plan will trigger the Jenkins job using a Herald rule that will be configured in the next section.

1. Navigate to `https://phabricator.example/harbormaster/plan/` with your base Phabricator URL in place of `phabricator.example`.
2. Click the **New Build Plan** button in the top right corner of the page.
3. Enter a name for the build plan in the **Plan Name** field. For these instructions, we'll use "test-example" as the build name.
4. Click the **Create Build Plan** button.
5. Click the **Add Build Step button**.
6. Click the **Make HTTP Request** step.
7. Use this template URI to fill in the URI field for the build plan: `https://ci.example.com/buildByToken/buildWithParameters?job=test-example&DIFF_ID=${buildable.diff}&PHID=${target.phid}`

	Be sure to replace `https://ci.example.com` with the URI of your Jenkins instance and `test-example` with the name of your Jenkins job.

	If your Jenkins instance is exposed to the internet, make sure to install the [Build Token Root Plugin][] and fill in the `token` parameter.

[Build Token Root Plugin]: https://wiki.jenkins-ci.org/display/JENKINS/Build+Token+Root+Plugin

8. Click the **When Complete** dropdown menu and select **Wait For Message**.
9. Click the **Create Build Step** button.
![Harbormaster plan](/docs/harbormaster-plan.png)

Herald
------

With the build plans created it's time to create a Herald Rule to trigger the plans. The steps below will configure a Herald Rule to trigger the build plans on Differential Revisions to your repository.

1. Navigate to `https://phabricator.example/herald/` with your base Phabricator URL in place of `phabricator.example`.
2. Click the **Create Herald Rule** button in the top right corner of the page.
3. Select the **Differential Revisions** checkbox and click **Continue**.
4. Select the **Global** checkbox and click **Continue**.
5. Enter a name for the Herald Rule in the **Rule Name** field.
6. In the **Conditions** section, click the dropdown menu that says "Author" and select "Repository".
7. Enter your repository name in to the blank field in the **Conditions** section.
8. In the **Actions** section, click the dropdown menu that says "Add blocking reviewers" and select "Run build plans".
9. Enter the build plans that were created in the previous section in to the blank field in the **Action** section.
10. Click **Save Rule**.

![Herald rule](/docs/herald-rule.png)

Test Your Configuration
-----------------------

Try `arc diff`-ing on your repo. If everything goes well, you should see Jenkins
commenting on your diff:

![Example](/docs/uberalls-integration.png)

Advanced Usage
--------------

Now that you have build status and optionally coverage data set up, check out some
[advanced features](docs/advanced.md).

Development
-----------

Use gradle to perform various development related tasks. [More info](https://wiki.jenkins-ci.org/display/JENKINS/Gradle+JPI+Plugin)


Testing
-------

Start up Jenkins with the plugin installed:
```bash
./gradlew server
```

Open your browser to your [local instance](http://localhost:8080).

Pull Requests
-------------

Please open all pull requests and issues against
https://github.com/uber/phabricator-jenkins-plugin.

License
-------

MIT Licensed
