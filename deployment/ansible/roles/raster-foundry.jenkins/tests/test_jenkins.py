import pytest


@pytest.fixture()
def AnsibleDefaults(Ansible):
    """ Load default variables into dictionary.

    Args:
        Ansible - Requires the ansible connection backend.
    """
    return Ansible("include_vars", "./defaults/main.yml")["ansible_facts"]


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


def test_crontab_entry(Ansible, AnsibleDefaults, Command, Sudo):
    """ Check the crontab for the Jenkins user to ensure that the entries
        for Jenkins restart and Docker garbage collection are present and
        correct.

        Args:
            Ansible - Obtain name of Jenkins user from azavea.jenkins defaults
            AnsibleDefaults - Get location of Jenkins CLI JAR and Jenkins HTTP
                              port.
            Command - Run crontab
            Sudo - Switch to Jenkins user

        Raises:
            AssertionError if Docker and Jenkins entries are not in the crontab

    """

    facts = Ansible("include_vars",
                    "../azavea.jenkins/defaults/main.yml")["ansible_facts"]

    jenkins_name = facts["jenkins_name"]
    jenkins_crontab_entry = "0 */3 * * * /usr/bin/java -jar {} " \
                            "-s http://localhost:{} restart".format(
                                AnsibleDefaults["jenkins_cli_jar_path"],
                                AnsibleDefaults["jenkins_http_port"])

    docker_crontab_entry = "@hourly /usr/bin/docker run --rm -v " \
                           "/var/run/docker.sock:/var/run/docker.sock -v " \
                           "/etc:/etc spotify/docker-gc"

    with Sudo(user=jenkins_name):
        crontab = Command.check_output("crontab -l")
        assert jenkins_crontab_entry in crontab
        assert docker_crontab_entry in crontab


def test_jenkins_cli_restart(Ansible, AnsibleDefaults, Command, Sudo):
    """ Ensure we can use the Jenkins CLI to restart the service without
    an inital login. In order for this to be the case, the Jenkins CLI
    JAR must be in the proper location and athorizations have to be turned off.

    Args:
        Ansible - get_url module to interact with Jenkins
        AnsibleDefaults - module to pull CLI Jar Location and Jenkins port
        Command - module to run the restart command
        Sudo - privilege escalation to reestart Jenkins

    Raises:
        Assertion error if command doesn't return 0, or if Jenkins doesn't
        return a status page saying it's restarting.
    """
    jenkins_cli_jar_path = AnsibleDefaults["jenkins_cli_jar_path"]
    jenkins_http_port = AnsibleDefaults["jenkins_http_port"]
    jenkins_url = "http://localhost:{}".format(jenkins_http_port)

    restart_command = "/usr/bin/java -jar {} " \
                      "-s {} restart".format(jenkins_cli_jar_path, jenkins_url)
    with Sudo():
        restart_jenkins = Command(restart_command).rc

    Command("sleep 15")

    jenkins_restart_message = Ansible("get_url",
                                      "url={} "
                                      "dest=/tmp/jenkins.html".format(jenkins_url), # NOQA
                                      check=False)["msg"]

    # Check to see that the restart command exited successfully
    assert restart_jenkins == 0

    # Check to ensure Jenkins has successfully restarted.
    assert "OK" in jenkins_restart_message
