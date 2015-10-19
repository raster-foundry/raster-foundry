{
    "variables": {
        "version": "",
        "branch": "",
        "aws_region": "",
        "aws_ubuntu_ami": "",
        "stack_type": ""
    },
    "builders": [
        {
            "name": "rf-app",
            "type": "amazon-ebs",
            "region": "{{user `aws_region`}}",
            "source_ami": "{{user `aws_ubuntu_ami`}}",
            "instance_type": "t2.large",
            "ssh_username": "ubuntu",
            "ami_name": "rf-app-{{timestamp}}-{{user `version`}}",
            "user_data_file": "cloud-config/packer-app-server.yml",
            "run_tags": {
                "PackerBuilder": "amazon-ebs"
            },
            "tags": {
                "Name": "rf-app",
                "Version": "{{user `version`}}",
                "Branch": "{{user `branch`}}",
                "Created": "{{ isotime }}",
                "Service": "Application",
                "Environment": "{{user `stack_type`}}"
            },
            "associate_public_ip_address": true
        },
        {
            "name": "rf-worker",
            "type": "amazon-ebs",
            "region": "{{user `aws_region`}}",
            "source_ami": "{{user `aws_ubuntu_ami`}}",
            "instance_type": "t2.large",
            "ssh_username": "ubuntu",
            "ami_name": "rf-worker-{{timestamp}}-{{user `version`}}",
            "run_tags": {
                "PackerBuilder": "amazon-ebs"
            },
            "tags": {
                "Name": "rf-worker",
                "Version": "{{user `version`}}",
                "Branch": "{{user `branch`}}",
                "Created": "{{ isotime }}",
                "Service": "Worker",
                "Environment": "{{user `stack_type`}}"
            },
            "associate_public_ip_address": true
        }
    ],
    "provisioners": [
        {
            "type": "shell",
            "inline": [
                "sleep 5",
                "sudo apt-get update -qq",
                "sudo apt-get install python-pip python-dev -y",
                "sudo pip install ansible==1.9.4",
                "sudo /bin/sh -c 'echo {{user `version`}} > /srv/version.txt'"
            ]
        },
        {
            "type": "ansible-local",
            "playbook_file": "ansible/app-servers.yml",
            "playbook_dir": "ansible",
            "inventory_file": "ansible/inventory/packer-app-server",
            "extra_arguments": [
                "--extra-vars 'deploy_branch={{user `version`}}'"
            ],
            "only": [
                "rf-app"
            ]
        },
        {
            "type": "ansible-local",
            "playbook_file": "ansible/workers.yml",
            "playbook_dir": "ansible",
            "inventory_file": "ansible/inventory/packer-worker-server",
            "extra_arguments": [
                "--extra-vars 'deploy_branch={{user `version`}}'"
            ],
            "only": [
                "rf-worker"
            ]
        }
    ]
}
