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