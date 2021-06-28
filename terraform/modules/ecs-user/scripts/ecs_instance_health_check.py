import os
import logging
import time
import socket
import requests
import boto3

'''
This code interrogates the ECS agent running on EC2 instances and for each task, compares the `DesiredStatus`
and `KnownStatus`. If for any task, those statuses are different for a period of time defined by 
`iteration_max` * `sleep_seconds`, the EC2 instance is marked as Unhealthy (and therefore purged from ECS).
'''


def setup_logging(logger_level):
    the_logger = logging.getLogger()
    for old_handler in the_logger.handlers:
        the_logger.removeHandler(old_handler)

    new_handler = logging.FileHandler("/var/log/ecs_instance_health_check.log")

    hostname = socket.gethostname()

    json_format = (
        '{ "timestamp": "%(asctime)s", "log_level": "%(levelname)s", "message": "%(message)s", '
        f'"environment": "{os.environ.get("ENVIRONMENT", "NOT_SET")}", "application": "{os.environ.get("APPLICATION", "NOT_SET")}", '
        f'"module": "%(module)s", "process": "%(process)s", '
        f'"thread": "[%(thread)s]", "hostname": "{hostname}" }} '
    )

    new_handler.setFormatter(logging.Formatter(json_format))
    the_logger.addHandler(new_handler)
    new_level = logging.getLevelName(logger_level.upper())
    the_logger.setLevel(new_level)

    if the_logger.isEnabledFor(logging.DEBUG):
        boto3.set_stream_logger()
        the_logger.debug(f'Using boto3", "version": "{boto3.__version__}"')

    return the_logger


logger = setup_logging(logging.INFO)

ecs_agent_url = "http://localhost:51678/v1/tasks"
iteration_max = 3
sleep_seconds = 60


def mark_ecs_instance_as_unhealthy():
    client = boto3.client('autoscaling', region_name=os.environ.get("REGION"))
    response = client.set_instance_health(
        InstanceId=os.environ.get("INSTANCE_ID"),
        HealthStatus="Unhealthy"
    )


def main():
    logger.info("ECS Instance health check started")
    tasks = {}  # Stores latest version of each ECS tasks returned by ECS agent
    while True:
        response = requests.get(ecs_agent_url)
        if response.status_code == 200:  # ECS Agent is responsive
            reponse_json = response.json()
            for task in reponse_json["Tasks"]:
                task_arn = task["Arn"]
                if task_arn not in tasks:  # Is returned task already in dictionnary?
                    tasks[task_arn] = {"count_iteration_with_unmatched_statuses": 0}
                tasks[task_arn]["desired_status"] = task["DesiredStatus"]
                tasks[task_arn]["known_status"] = task["KnownStatus"]
                if tasks[task_arn]["desired_status"] == tasks[task_arn]["known_status"]:
                    tasks[task_arn]["count_iteration_with_unmatched_statuses"] = 0
                else:
                    tasks[task_arn]["count_iteration_with_unmatched_statuses"] += 1
                # Marks instance as `Unhealthy` if `DesiredStatus` and `KnownStatus`
                # are still different after (`iteration_max` * `sleep_seconds`) seconds
                if tasks[task_arn]["count_iteration_with_unmatched_statuses"] == iteration_max:
                    logging.warning("""Task {0} DesiredStatus: {1} and
                    KnownStatus: {2} unmatched {3} seconds.
                    ECS instance marked as unhealthly""".format(task_arn,
                                                                tasks[task_arn]["desired_status"],
                                                                tasks[task_arn]["known_status"],
                                                                iteration_max * sleep_seconds))
                    mark_ecs_instance_as_unhealthy()
                    return 0
                else:
                    logging.info("{0} is healthly".format(task_arn))
            logger.info("ECS agent is running")
            time.sleep(sleep_seconds)
        else:
            logger.warning("ECS agent is not running")
            mark_ecs_instance_as_unhealthy()
            return 0


if __name__ == "__main__":
    main()
