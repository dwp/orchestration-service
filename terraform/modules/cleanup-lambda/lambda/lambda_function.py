import boto3
import urllib3
import json
import os

table_name = os.environ.get("TABLE_NAME")
http = urllib3.PoolManager()
dynamodb = boto3.resource("dynamodb", region_name="")  # TODO: add region as env.var. to be passed in
table = dynamodb.Table("tableName")  # TODO: add table_name as env.var. to be passed in

def query_active_users():
    userObjects = table.scan(ProjectionExpression = "username")["Items"]
    userList = []
    for user in userObjects:
        userList.append(user["username"])
    return userList

def lambda_handler(event, context):
    userList = query_active_users()
    data = {'usernames': userList}
    endpoint = ""  # TODO: add env. var. for URL for OS and add "/cleanup"
    encoded_data = json.dumps(data).encode('utf-8')

    resp = http.request(
        'POST',
        endpoint,
        body=encoded_data,
        headers={'Content-Type': 'application/json'})

    data = json.loads(resp.data.decode('utf-8'))['json']
    return data
