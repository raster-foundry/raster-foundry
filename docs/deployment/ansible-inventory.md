# Dynamic Ansible Inventory

# TL;DR
You can use Ansible to interact with `jenkins` and `bastion` hosts by doing `ansible <groupname> <ansible arguments>`.

```
$ ansible jenkins -m shell -a "echo hello"

52.23.252.118 | SUCCESS | rc=0 >>
hello
```
You can also reference the host groups in an Ansible playbook task.

<pre>
<code>
$ cat playbook.yml
---
- hosts: <b>jenkins</b>
  connection: local
  become: True
...
</code>
</pre>

# Table of contents
[Setup](#setup)
[`EC2.py`](#ec2py)
[Static Inventory Files](#static-inventory-files)
## Setup
The [Ansible inventory](http://docs.ansible.com/ansible/intro_inventory.html) for this project is generated using a combination of `ec2.py` -- an Ansible [dynamic inventory script](http://docs.ansible.com/ansible/intro_dynamic_inventory.html#example-aws-ec2-external-inventory-script) -- and static inventory files. `ec2.py` allows us to dynamically pull a list of active EC2 instances from AWS, and the static inventory files place those instances into static host groups to allow for easy interaction with Ansible. This way, you can use Ansible to provision a Jenkins master or run updates on a Bastion host, regardless of that host's current IP address.

Using this script requires some dependency installation and AWS profile configuration:

* Install the `ec2.py` dependencies. Run: 
```
$ cd raster-foundry/deployment
$ pip install -r requirements.txt
``` 

* Setup your `raster-foundry` AWS profile.
```
$ aws --profile raster-foundry configure
AWS Access Key ID [****************BF6Q]: 
AWS Secret Access Key [****************L4zm]: 
Default region name [us-east-1]: 
Default output format [None]: 

```

* Add the `raster-foundry` keypair
```
$ ssh-agent bash
$ ssh-add /path/to/raster-foundry/keypair
Identity added:  /path/to/raster-foundry/keypair (/path/to/raster-foundry/keypair)
```


## ec2.py
Once those steps are finished, you can see a listing of available hosts (grouped both by IP address and EC2 metadata fields) and the associated EC2 metadata by running `inventory/ec2.py`
```
$ cd raster-foundry/deployment/ansible
$ ./inventory/ec2.py

{
  "_meta": {
    "hostvars": {
      "52.23.252.118": {
        "ansible_ssh_host": "52.23.252.118", 
        ...
      }, 
      "52.90.248.201": {
        "ansible_ssh_host": "52.90.248.201", 
        ...
      }, 
      "dbstaging_cgjzly4pr21k_us_east_1_rds_amazonaws_com": {
        "ansible_ssh_host": "dbstaging.cgjzly4pr21k.us-east-1.rds.amazonaws.com", 
        ...
      }
    }
  }, 
  ...
  "us-east-1": [
    "52.23.252.118", 
    "52.90.248.201", 
    "dbstaging_cgjzly4pr21k_us_east_1_rds_amazonaws_com"
  ], 
  "us-east-1c": [
    "52.23.252.118", 
    "52.90.248.201"
  ], 
  "us-east-1e": [
    "dbstaging_cgjzly4pr21k_us_east_1_rds_amazonaws_com"
  ], 
  "vpc_id_vpc_3b58385c": [
    "52.23.252.118", 
    "52.90.248.201", 
    "dbstaging_cgjzly4pr21k_us_east_1_rds_amazonaws_com"
  ]
}

```
For more information about the output of `ec2.py`, please see [the Ansible documentation](http://docs.ansible.com/ansible/intro_dynamic_inventory.html#example-aws-ec2-external-inventory-script). 


## Static Inventory files
The `inventory` directory also contains some static inventory files. These files place the hosts returned by `ec2.py` into logical groups using the instance tags. `ec2.py` gives the tag name...

<pre>
<code>
$ cd raster-foundry/deployment/ansible 
$ ./inventory/ec2.py
{
  "_meta": {
    ...
  }, 
  ...,
  <b>"tag_Name_Jenkins"</b>: [
    "52.23.252.118", 
    "52.23.252.118"
  ], 
  ...
}
</code>
</pre>

...which is then captured in a static grouping.

<pre>
<code>
raster-foundry/deployment/ansible $ cat inventory/jenkins

[tag_Name_Jenkins]

<b>[jenkins:children]
tag_Name_Jenkins</b>

[jenkins:vars]
ansible_user=ubuntu
</code>
</pre>

With this configuration, we can interact with any hosts in the `jenkins` and `bastion` groups by doing `ansible <groupname> <ansible args>`
```
$ cd raster-foundry/deployment/ansible 
$ ansible jenkins -m shell -a "echo hello"
52.23.252.118 | SUCCESS | rc=0 >>
hello
```

These host groups are also accessible via the `hosts` section in an Ansible playbook task. 

<pre>
<code>
$ cat playbook.yml
---
- hosts: <b>jenkins</b>
  connection: local
  become: True
...
</code>
</pre>