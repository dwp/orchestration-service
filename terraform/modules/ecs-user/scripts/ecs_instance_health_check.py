import os
import logging
import time
import requests
import boto3

logging.basicConfig(filename='/var/log/ecs_instance_health_check.log', encoding='utf-8', level=logging.INFO)

ecs_agent_url = "http://localhost:51678/v1/tasks"
iteration_max = 3
sleep_seconds = 60


def mark_ecs_instance_as_unhealthy():
    client = boto3.client('autoscaling', region_name=os.environ.get("REGION"))
    # response = client.set_instance_health(
    #     InstanceId=os.environ.get("INSTANCE_ID"),
    #     HealthStatus="Unhealthy"
    # )


def main():
    logging.info("ECS Instance health check started")
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
            logging.info("ECS agent is running")
            time.sleep(sleep_seconds)
        else:
            logging.warning("ECS agent is not running")
            mark_ecs_instance_as_unhealthy()
            return 0


if __name__ == "__main__":
    main()
