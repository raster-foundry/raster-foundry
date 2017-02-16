# Running migrations on staging instances

The following instructions can be used to apply migration commands on an AWS stack of this project.

## Prerequisites

- Access to the AWS Web Console

## Console commands

 - First, log into the [AWS Web Console](https://raster-foundry.signin.aws.amazon.com/console) and navigate to the [EC2 Container Service](https://console.aws.amazon.com/ecs/home?region=us-east-1#/clusters) dashboard.

 - Go to the [Task Definitions](https://console.aws.amazon.com/ecs/home?region=us-east-1#/taskDefinitions) tab, and look for the migration task called `StagingAppMigrations`.

![image](https://cloud.githubusercontent.com/assets/2507188/19808961/1c258a8c-9cf5-11e6-870f-a085a32ba526.png)

 - Click the `StagingAppMigrations` link to get to the task definition page.

![image](https://cloud.githubusercontent.com/assets/2507188/19807340/a5232dec-9ced-11e6-9baf-b120fd314f84.png)

 - Click the task defintion name to get taken to the details page, then select ***Run Task*** from the ***Actions*** menu.

![image](https://cloud.githubusercontent.com/assets/2507188/19807341/a5272668-9ced-11e6-91ab-f15acd58042f.png)

 - Expand the ***Advanced Options*** section, and then expand the ***Container Overrides*** section for the `app-migrations` container.

![image](https://cloud.githubusercontent.com/assets/2507188/19807328/940811b2-9ced-11e6-9c7e-b322807e88f4.png)

 - In the ***Command override*** field, enter `mg update`.

![image](https://cloud.githubusercontent.com/assets/2507188/19807329/940a6188-9ced-11e6-9646-168d7b955515.png)

When finished, click ***Run Task***. The task will execute immediately. To see the results of the task, go to the AWS CloudWatch dashboard, and [view the logs](https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#logStream:group=logStagingAppMigrations) for the `StagingAppMigrations` task definition.

Repeat this process again to run `mg apply` and apply the migrations.