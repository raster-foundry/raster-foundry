import pytest
from os.path import dirname, realpath, join
import re


@pytest.fixture()
def AnsibleDefaults(Ansible):
    """ Load default variables into dictionary.

    Args:
        Ansible - Requires the ansible connection backend.
    """
    return Ansible("include_vars", "./defaults/main.yml")["ansible_facts"]


@pytest.fixture()
def JenkinsRoleDefaults(Ansible):
    """ Load default variables into dictionary.

    Args:
        Ansible - Requires the ansible connection backend.
    """
    roles_dir = dirname(dirname(dirname(realpath(__file__))))
    jenkins_defaults_file = join(roles_dir, "azavea.jenkins/defaults/main.yml")
    return Ansible("include_vars", jenkins_defaults_file)["ansible_facts"]


@pytest.fixture()
def AnsibleFacts(Ansible):
    """ Load ansible facts into a dictionary.

    Args:
        Ansible - Requires ansible connection backend.
    """
    return Ansible("setup")["ansible_facts"]


def test_jenkins_cli_jar_exists(File, AnsibleDefaults):
    """ Ensure the Jenkins CLI JAR exists.

    Args:
        File - module to determine CLI file existence
        AnsibleDefaults - module to pull the Jenkins CLI JAR path.

    Raises:
        AssertionError if package is not installed or the wrong version.
    """
    jenkins_cli_jar_path = AnsibleDefaults["jenkins_cli_jar_path"]
    jenkins_cli_jar = File(jenkins_cli_jar_path)
    assert jenkins_cli_jar.exists


def test_docker_cronjob(Command, Sudo, JenkinsRoleDefaults):
    docker_crontab_entry = "@hourly /usr/bin/docker run --rm -v " \
                           "/var/run/docker.sock:/var/run/docker.sock -v " \
                           "/etc:/etc spotify/docker-gc"

    with Sudo(user=JenkinsRoleDefaults["jenkins_name"]):
        crontab = Command.check_output("crontab -l")
        assert docker_crontab_entry in crontab


def test_jenkins_config(File):
    """ Make sure jenkins file is correct """
    jenkins_conf = File("/var/lib/jenkins/config.xml").content_string
    assert re.findall("<useSecurity>false</useSecurity>", jenkins_conf)
    assert re.findall("<numExecutors> 1</numExecutors>", jenkins_conf)
    assert not (re.findall("<authorizationStrategy", jenkins_conf))
    assert not (re.findall("<securityRealm>", jenkins_conf))


def test_jenkins_groups(User, JenkinsRoleDefaults):
    """ Ensure Jenkins is in the right groups
    Args:
        User: module to determine Jenkins group membership.
    """
    jenkins_name = JenkinsRoleDefaults["jenkins_name"]
    jenkins_groups = User(jenkins_name).groups
    assert set(["adm", "sudo", "jenkins"]).issubset(jenkins_groups)
