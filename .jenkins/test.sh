#!/bin/bash

set -ex
set -o pipefail

mvn -X -e clean package
