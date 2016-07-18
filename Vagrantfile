# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.require_version ">= 1.8"

if ["up", "provision", "status"].include?(ARGV.first)
  require_relative "helpers/ansible_galaxy_helper"

  AnsibleGalaxyHelper.install_dependent_roles("deployment/ansible")
end

ANSIBLE_GROUPS = {
  "template" => ["template"]
}

if !ENV["VAGRANT_ENV"].nil? && ENV["VAGRANT_ENV"] == "TEST"
  ANSIBLE_ENV_GROUPS = {
    "test:children" => [
      # "app-servers", "services", "workers"
    ]
  }
  VAGRANT_NETWORK_OPTIONS = { auto_correct: true }
else
  ANSIBLE_ENV_GROUPS = {
    "development:children" => [
      # "app-servers", "services", "workers"
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

  config.vm.define "template" do |template|
    template.vm.hostname = "template"
    template.vm.network "private_network", ip: ENV.fetch("RF_TEMPLATE_IP", "97.97.50.50")

    template.vm.synced_folder ".", "/vagrant", disabled: true

    # Service 1
    template.vm.network "forwarded_port", {
      guest: 1234,
      host: 1234
    }.merge(VAGRANT_NETWORK_OPTIONS)

    template.vm.provider "virtualbox" do |v|
      v.memory = 1024
    end

    # Ansible (?)
    template.vm.provision "ansible" do |ansible|
      ansible.playbook = "deployment/ansible/template.yml"
      ansible.groups = ANSIBLE_GROUPS.merge(ANSIBLE_ENV_GROUPS)
      ansible.raw_arguments = ["--timeout=60"]
    end
  end
end
