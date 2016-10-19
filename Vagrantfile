# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version ">= 1.8"

if ["up", "provision", "status"].include?(ARGV.first)
  require_relative "deployment/vagrant/ansible_galaxy_helper"

  AnsibleGalaxyHelper.install_dependent_roles("deployment/ansible")
end

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
  # database
  config.vm.network :forwarded_port, guest: 5432, host: Integer(ENV.fetch("RF_PORT_5432", 5432))
  # swagger editor
  config.vm.network :forwarded_port, guest: 8888, host: Integer(ENV.fetch("RF_PORT_9090", 9090))
  # nginx
  config.vm.network :forwarded_port, guest: 9100, host: Integer(ENV.fetch("RF_PORT_9100", 9100))
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

  config.vm.provider :virtualbox do |vb|
    vb.memory = 4096
    vb.cpus = 2
  end

  config.vm.provision "shell" do |s|
    s.inline = <<-SHELL
      if [ ! -x /usr/local/bin/ansible ]; then
        sudo apt-get update -qq
        sudo apt-get install python-pip python-dev -y
        sudo pip install paramiko==1.16.0
        sudo pip install ansible==2.0.2.0
      fi

      cd /opt/raster-foundry/deployment/ansible && \
      ANSIBLE_FORCE_COLOR=1 PYTHONUNBUFFERED=1 ANSIBLE_CALLBACK_WHITELIST=profile_tasks \
      ansible-playbook -u vagrant -i 'localhost,' --extra-vars "aws_profile=raster-foundry" \
          raster-foundry.yml
      cd /opt/raster-foundry
      export AWS_PROFILE=raster-foundry
      su vagrant ./scripts/bootstrap
      su vagrant ./scripts/setup
    SHELL
  end
end
