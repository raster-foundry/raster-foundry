# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version ">= 1.8"

if ["up", "provision", "status"].include?(ARGV.first)
  require_relative "deployment/vagrant/ansible_galaxy_helper"

  AnsibleGalaxyHelper.install_dependent_roles("deployment/ansible")
end

ANSIBLE_VERSION = "2.3.1.0"

Vagrant.configure(2) do |config|
  config.vm.box = "ubuntu/trusty64"

  # Wire up package caching:
  if Vagrant.has_plugin?("vagrant-cachier")
    config.cache.scope = :machine
  end

  config.vm.synced_folder ".", "/vagrant", disabled: true
  config.vm.synced_folder ".", "/opt/raster-foundry"
  config.vm.synced_folder "~/.aws", "/home/vagrant/.aws"

  # application server
  config.vm.network :forwarded_port, guest: 9000, host: Integer(ENV.fetch("RF_PORT_9000", 9000))
  # tileserver
  config.vm.network :forwarded_port, guest: 9900, host: Integer(ENV.fetch("RF_PORT_9900", 9900))
  # nginx-api
  config.vm.network :forwarded_port, guest: 9100, host: Integer(ENV.fetch("RF_PORT_9100", 9100))
  # nginx-tileserver
  config.vm.network :forwarded_port, guest: 9101, host: Integer(ENV.fetch("RF_PORT_9101", 9101))
  # airflow webserver editor
  config.vm.network :forwarded_port, guest: 8080, host: Integer(ENV.fetch("RF_PORT_8080", 8080))
  # airflow flower editor
  config.vm.network :forwarded_port, guest: 5555, host: Integer(ENV.fetch("RF_PORT_5555", 5555))
  # spark master
  config.vm.network :forwarded_port, guest: 8888, host: Integer(ENV.fetch("RF_PORT_8888", 8888))
  # spark worker
  config.vm.network :forwarded_port, guest: 8889, host: Integer(ENV.fetch("RF_PORT_8888", 8889))
  # spark driver
  config.vm.network :forwarded_port, guest: 4040, host: Integer(ENV.fetch("RF_PORT_4040", 4040))
  # graphite ui
  config.vm.network :forwarded_port, guest: 8181, host: Integer(ENV.fetch("RF_PORT_8181", 8181))
  # grafana ui
  config.vm.network :forwarded_port, guest: 3000, host: Integer(ENV.fetch("RF_PORT_4040", 3000))
  # jmx api
  config.vm.network :forwarded_port, guest: 9010, host: Integer(ENV.fetch("RF_PORT_9010", 9010))
  # jmx tile
  config.vm.network :forwarded_port, guest: 9020, host: Integer(ENV.fetch("RF_PORT_9020", 9020))

  config.vm.provider :virtualbox do |vb|
    vb.memory = 8096
    vb.cpus = 2
  end

  host_user = ENV.fetch("USER", "vagrant")
  aws_profile = ENV.fetch("RF_AWS_PROFILE", "raster-foundry")
  rf_settings_bucket = ENV.fetch("RF_SETTINGS_BUCKET",
                                "rasterfoundry-development-config-us-east-1")
  rf_artifacts_bucket = ENV.fetch("RF_ARTIFACTS_BUCKET",
                                   "rasterfoundry-global-artifacts-us-east-1")

  config.vm.provision "shell" do |s|
    s.inline = <<-SHELL
      if [ ! -x /usr/local/bin/ansible ] || ! ansible --version | grep #{ANSIBLE_VERSION}; then
        sudo apt-get update -qq
        sudo apt-get install python-pip python-dev -y
        sudo pip install paramiko==1.16.0
        sudo pip install ansible==#{ANSIBLE_VERSION}
      fi

      cd /opt/raster-foundry/deployment/ansible && \
      ANSIBLE_FORCE_COLOR=1 PYTHONUNBUFFERED=1 ANSIBLE_CALLBACK_WHITELIST=profile_tasks \
      ansible-playbook -u vagrant -i 'localhost,' \
          --extra-vars "host_user=#{host_user} aws_profile=#{aws_profile} \
                        rf_settings_bucket=#{rf_settings_bucket} \
                        rf_artifacts_bucket=#{rf_artifacts_bucket}" \
          raster-foundry.yml
      cd /opt/raster-foundry

      export AWS_PROFILE=#{aws_profile}
      export RF_SETTINGS_BUCKET=#{rf_settings_bucket}
      export RF_ARTIFACTS_BUCKET=#{rf_artifacts_bucket}
      su vagrant ./scripts/bootstrap
      su vagrant ./scripts/setup
    SHELL
  end
end
