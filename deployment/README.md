# Amazon Web Services Deployment

Deployment is driven by [Packer](https://www.packer.io), [Troposphere](https://github.com/cloudtools/troposphere), the [AWS Command Line Interface](http://aws.amazon.com/cli/) and [Boto](http://aws.amazon.com/cli/), the AWS SDK for Python. It requires some setup in your Amazon account, editing a configuration file, creating AMIs, and launching a stack.

## Dependencies

To install the Python deployment dependencies, use `pip`:

```bash
$ cd deployment
$ pip install -r requirements.txt
```

Next, install [Packer](https://packer.io/) using the steps detailed on Packer's [website](https://packer.io/downloads.html).

## Create Identity and Access Management (IAM) roles

See [Create an IAM Instance Profile for Your Amazon EC2 Instances](http://docs.aws.amazon.com/codedeploy/latest/userguide/how-to-create-iam-instance-profile.html).

## Configure an AWS Profile using the AWS CLI

Using the AWS CLI, create an AWS profile:

```bash
$ aws configure --profile rf-stg
```

You will be prompted to enter your AWS credentials, along with a default region. These credentials will be used to authenticate calls to the AWS API when using Boto, Packer, and the AWS CLI.

### Generating AMIs

Before launching the Raster Foundry stack, AMIs for each service need to be generated:

```bash
$ ./rf_stack.py create-ami --aws-profile rf-stg --rf-profile staging \
                            --machine-type rf-{app,worker}
```
