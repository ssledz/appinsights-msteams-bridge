#!/bin/bash

mvn clean package
mvn azure-functions:deploy
