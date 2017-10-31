Advanced Usage
==============

In addition to Unit and Coverage results, this plugin supports two additional features
you may want to take use of. Both options take a maximum number of bytes to copy, since the plugin
is executed on the Jenkins master and syncing multiple megabytes of data can cause
performance problems.

Custom Comments
---------------

If you'd like to post additional text back to Phabricator, you can add text to the `.phabricator-comment` file
(you can change the name of the file in your job configuration page):

![Comment Configuration](/docs/custom-comment.png)

Any text present in this file will be echoed verbatim to the comment that Jenkins posts back to
Phabricator. If you'd like to preserve formatting, check the "Preserve Formatting" block and the
plugin will surround the comment in triple-backticks (```)

Custom Lint
-----------

If you'd like to send Lint Violations as well, you can echo Harbormaster-compatible JSON
(where each line is a valid JSON dictionary) into the `.phabricator-lint` file.

Although each **line** must be valid JSON, note that the file as a whole is **not valid JSON**
(e.g. if you call `JSON.parse('.phabricator-lint')`) it will fail. This is a design decision to
make it easy for build scripts to `tee` (echo line-by-line) violations without having to
produce well-formed JSON, which requires knowing upfront how many violations are present.

![Lint Configuration](/docs/custom-lint.png)

If a job was configured to run the following shell script:

```bash
mkdir example
echo "Once upon a time\nThere was a Jenkins plugin" > example/path
# NOTE: tee -a to support appending multiple lints
echo '{"name": "Comment Name", "code": "Haiku", "severity": "error", "path": "example/path", "line": 2, "char": 0, "description": "Line is not a Haiku" }' | tee -a .phabricator-lint
```

You would see the following in your differential at the top:

![Inline Diff Lint](/docs/example-path-haiku.png)

And the following in the code view:

![Inline Diff Lint](/docs/inline-haiku.png)

See [Harbormaster Lint](https://secure.phabricator.com/conduit/method/harbormaster.sendmessage/)
API for details on the supported JSON keys. `line`, `char`, and `description` are all optional.
The rest are required.

The severity parameter recognizes these severity levels:

| Key      | Name     |
|----------|----------|
| advice   | Advice   |
| autofix  | Auto-Fix |
| warning  | Warning  |
| error    | Error    |
| disabled | Disabled |

Suspend Useless Jobs
---------------------

When new builds are triggered from Phabricator due to new changes to the same diff or
rebuilding via harbormaster, it may be desirable to suspend existing jobs that were triggered
for the same diff. This can be done by adding the `ABORT_ON_REVISION_ID` string parameter to your job.

![abort on revision id parameter](/docs/jenkins-suspend-param.png)

You need to also add the parameter to your harbormaster request
![abort on revision id parameter](/docs/harbormaster-suspend-param.png)

This makes the latest build triggered for a diff automatically abort the existing running builds
for the same diff on the job. Jobs aborted this way will skip notifying phabricator to
avoid confusion. Please note that builds on the same diff triggered by the same upstream build will not be aborted this way. This can be useful when running multi-configuration jobs and parallel builds that run on the same diff.

Also note that if you pass additional arguments to your harbormaster request they may need to be included in the `ABORT_ON_REVISION_ID` field as well. A good example is when you use the same CI job to build multiple targets on a single diff. So for example, if the jenkins request params have
`TARGET=some_target`, then to ensure other targets are not cancelled for the same diff, you may want to set `ABORT_ON_REVISION_ID=some_target_${buildable.revision}`.

Pipeline
--------

Typically the Phabricator Notifier is used as a reporting step in a Jenkins Pipeline. The test result collector step must be run before the Notifier.

```groovy
stage ('report') {
    //...
    // junit()
    step([$class: 'PhabricatorNotifier', commentOnSuccess: true, commentWithConsoleLinkOnFailure: true])
    //...
}

```
