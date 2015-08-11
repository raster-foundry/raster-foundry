# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version ">= 1.6"

if ["up", "provision", "status"].include?(ARGV.first)
  require_relative "helpers/ansible_galaxy_helper"

  AnsibleGalaxyHelper.install_dependent_roles("deployment/ansible")
end

ANSIBLE_GROUPS = {
  "app-servers" => [ "app" ],
  "services" => [ "services" ],
  "monitoring-servers" => [ "services" ]
}

if !ENV["VAGRANT_ENV"].nil? && ENV["VAGRANT_ENV"] == "TEST"
  ANSIBLE_ENV_GROUPS = {
    "test:children" => [
      "app-servers", "services"
    ]
  }
  VAGRANT_NETWORK_OPTIONS = { auto_correct: true }
else
  ANSIBLE_ENV_GROUPS = {
    "development:children" => [
      "app-servers", "services"
    ]
  }
  VAGRANT_NETWORK_OPTIONS = { auto_correct: false }
end

VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box = "ubuntu/trusty64"

  # Wire up package caching:
  if Vagrant.has_plugin?("vagrant-cachier")
    config.cache.scope = :machine
  end

  config.vm.define "services" do |services|
    services.vm.hostname = "services"
    services.vm.network "private_network", ip: ENV.fetch("RF_SERVICES_IP", "33.33.40.80")

    services.vm.synced_folder ".", "/vagrant", disabled: true

    # Graphite Web
    services.vm.network "forwarded_port", {
      guest: 8080,
      host: 8080
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # Kibana
    services.vm.network "forwarded_port", {
      guest: 5601,
      host: 5601
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # PostgreSQL
    services.vm.network "forwarded_port", {
      guest: 5432,
      host: 5432
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # Pgweb
    services.vm.network "forwarded_port", {
      guest: 5433,
      host: 5433
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # Redis
    services.vm.network "forwarded_port", {
      guest: 6379,
      host: 6379
    }.merge(VAGRANT_NETWORK_OPTIONS)

    services.vm.provider "virtualbox" do |v|
      v.memory = 1024
    end

    services.vm.provision "ansible" do |ansible|
      ansible.playbook = "deployment/ansible/services.yml"
      ansible.groups = ANSIBLE_GROUPS.merge(ANSIBLE_ENV_GROUPS)
      ansible.raw_arguments = ["--timeout=60"]
    end
  end

  config.vm.define "app" do |app|
    app.vm.hostname = "app"
    app.vm.network "private_network", ip: ENV.fetch("RF_APP_IP", "33.33.40.81")

    app.vm.synced_folder ".", "/vagrant", disabled: true

    if Vagrant::Util::Platform.windows? || Vagrant::Util::Platform.cygwin?
      app.vm.synced_folder "src/rf", "/opt/app/", type: "rsync", rsync__exclude: ["node_modules/", "apps/"]
      app.vm.synced_folder "src/rf/apps", "/opt/app/apps"
    else
      app.vm.synced_folder "src/rf", "/opt/app/"
    end

    # Django via Nginx/Gunicorn
    app.vm.network "forwarded_port", {
      guest: 80,
      host: 8000
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # Livereload server
    app.vm.network "forwarded_port", {
      guest: 35729,
      host: 35729,
    }.merge(VAGRANT_NETWORK_OPTIONS)
    # Testem server
    app.vm.network "forwarded_port", {
      guest: 7357,
      host: 7357
    }.merge(VAGRANT_NETWORK_OPTIONS)

    app.ssh.forward_x11 = true

    app.vm.provider "virtualbox" do |v|
      v.memory = 1024
    end

    app.vm.provision "ansible" do |ansible|
      ansible.playbook = "deployment/ansible/app-servers.yml"
      ansible.groups = ANSIBLE_GROUPS.merge(ANSIBLE_ENV_GROUPS)
      ansible.raw_arguments = ["--timeout=60"]
    end
  end
end
