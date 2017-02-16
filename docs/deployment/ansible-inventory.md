# Ansible Inventory

# TL;DR
You can use Ansible to interact with `jenkins` and `bastion` hosts by doing `ansible <groupname> <ansible arguments>`. 

***NOTE:*** Ansible should either be run from the `deployments/ansible` directory, or run with `deployment/ansible/inventory` as the inventory (which can be specified with the `-i` flag).

```
$ cd raster-foundry/deployment/ansible
$ ansible jenkins -m shell -a "echo hello"

52.23.252.118 | SUCCESS | rc=0 >>
hello
```

or 

```
$ ansible -i deployment/ansible/inventory jenkins -m shell -a "echo hello"

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

## Setup 

Add the `raster-foundry` keypair
```
$ ssh-agent bash
$ ssh-add /path/to/raster-foundry/keypair
Identity added:  /path/to/raster-foundry/keypair (/path/to/raster-foundry/keypair)
```

Once `ssh-agent` has the raster foundry keypair added, you can interact with any hosts in the `jenkins` and `bastion` groups by doing `ansible <groupname> <ansible args>`.


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

For further information, please visit the Ansible [getting started](http://docs.ansible.com/ansible/intro_getting_started.html) documentation, from which this page was adapted.