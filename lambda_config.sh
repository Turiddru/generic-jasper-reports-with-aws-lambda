#!/bin/bash
# Configuration properties
docker_maven_version='maven:3.3-jdk-8'
docker_aws_cli_version='amazon/aws-cli:2.2.8'

lambda_packaged_file='jasperreports-1.0.1.jar'

region='eu-south-1'
files_s3_bucket='jr-lambda-files'
templates_s3_bucket='jr-jasperreports-templates'