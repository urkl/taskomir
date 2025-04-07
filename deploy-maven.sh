#!/bin/bash

mvn gitflow:release-start

mvn clean deploy

mvn gitflow:release-finish