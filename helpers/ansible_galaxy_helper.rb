require "yaml"

module AnsibleGalaxyHelper
  # Deserialize Ansible Galaxy installation metadata for a role
  def self.galaxy_install_info(role_path)
    galaxy_install_info = File.join(role_path, "meta", ".galaxy_install_info")

    if (File.directory?(role_path) || File.symlink?(role_path)) && File.exists?(galaxy_install_info)
      YAML.load_file(galaxy_install_info)
    else
      { install_date: "", version: "0.0.0" }
    end
  end

  # Uses the contents of roles.txt to ensure that ansible-galaxy is run
  # if any dependencies are missing
  def self.install_dependent_roles(ansible_directory)
    ansible_roles_txt = File.join(ansible_directory, "roles.txt")

    File.foreach(ansible_roles_txt) do |line|
      role_name, role_version = line.split(",")
      role_path = File.join(ansible_directory, "roles", role_name)
      galaxy_metadata = galaxy_install_info(role_path)

      if galaxy_metadata["version"] != role_version.strip
        unless system("ansible-galaxy install -f -r #{ansible_roles_txt} -p #{File.dirname(role_path)}")
          $stderr.puts "\nERROR: An attempt to install Ansible role dependencies failed."
          exit(1)
        end

        break
      end
    end
  end
end
