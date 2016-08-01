variable "project" {
  default = "Raster Foundry"
}

variable "environment" {
  default = "Staging"
}

variable "aws_region" {
  default = "us-east-1"
}

variable "aws_availability_zones" {}

variable "aws_key_name" {}

variable "vpc_cidr_block" {
  default = "10.0.0.0/16"
}

variable "external_access_cidr_block" {
  default = "66.212.12.106/32"
}

variable "vpc_private_subnet_cidr_blocks" {
  default = "10.0.1.0/24,10.0.3.0/24"
}

variable "vpc_public_subnet_cidr_blocks" {
  default = "10.0.0.0/24,10.0.2.0/24"
}

variable "bastion_ami" {}

variable "bastion_instance_type" {}
