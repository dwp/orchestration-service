import boto3
import urllib3
import json

http = urllib3.PoolManager()
dynamodb = boto3.resource("dynamodb", region_name="")  # TODO: add region as env.var. to be passed in
table = dynamodb.Table("tableName")  # TODO: add table_name as env.var. to be passed in

def query_active_users():
    userObjects = table.scan(ProjectionExpression="username")["Items"]
    userList = []
    for user in userObjects:
        userList.append(user["username"])
    return userList

def lambda_handler(event, context):
    userList = query_active_users()
    postObj = {'username': userList}
    encoded_data = json.dumps(postObj).encode('utf-8')
    endpoint = ""  # TODO: add env. var. for URL for OS and add "/cleanup"
    resp = http.request('POST', endpoint, headers={'ContentType': 'application/json'}, body=encoded_data)
    print(json.loads(resp.data.decode('utf-8'))['json'])
