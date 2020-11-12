import boto3

iam_client = boto3.client('iam')

"""
============================================================================================================
=================================== Helper methods that interact with AWS ==================================
============================================================================================================
"""


def list_all_policies_in_account():
    policy_array = []
    get_paginated_policies_using_marker(iam_client.list_policies(Scope='All'), policy_array)
    return policy_array


# list_policies() call is paginated - set a marker and continue recursively
def get_paginated_policies_using_marker(aws_api_reponse, policy_array):
    policy_array.extend(aws_api_reponse['Policies'])
    if (aws_api_reponse['IsTruncated']):
        res = iam_client.list_policies(Scope='All', Marker=aws_api_reponse['Marker'])
        policy_array.extend(res['Policies'])
        get_paginated_policies_using_marker(res, policy_array)
    else:
        return policy_array


def get_policy_statement_as_list(arn, default_version_id):
    policy_version = iam_client.get_policy_version(
        PolicyArn=arn,
        VersionId=default_version_id
    )
    return policy_version['PolicyVersion']['Document']['Statement']


def create_policy_from_json_and_return_arn(policy_name, json_document):
    policy = iam_client.create_policy(
        PolicyName=policy_name,
        PolicyDocument=json_document
    )
    policy_arn = policy['Policy']['Arn']
    wait_for_policy_to_exist(policy_arn)
    return policy_arn


def remove_policy_being_replaced(policy_arn, role_name):
    # removes from role
    iam_client.detach_role_policy(
        RoleName=role_name,
        PolicyArn=policy_arn
    )
    # deletes policy
    iam_client.delete_policy(
        PolicyArn=policy_arn
    )


def attach_policy_to_role(policy_arn, role):
    iam_client.attach_role_policy(
        RoleName=role,
        PolicyArn=policy_arn
    )


def wait_for_policy_to_exist(arn):
    waiter = iam_client.get_waiter('policy_exists')
    waiter.wait(
        PolicyArn=arn,
        WaiterConfig={
            'Delay': 5,
            'MaxAttempts': 6
        }
    )


def get_all_role_tags(role_name):
    result = iam_client.list_role_tags(
        RoleName=role_name,
    )
    result_array = []
    return get_paginated_role_tags_using_marker(result, result_array, role_name)


# list_role_tags() call is paginated - set a marker and continue recursively
def get_paginated_role_tags_using_marker(result, result_array, role_name):
    result_array.extend(result['Tags'])
    if (result['IsTruncated']):
        res = iam_client.list_role_tags(RoleName=role_name, Marker=result['Marker'])
        result_array.extend(res['Policies'])
        get_paginated_role_tags_using_marker(res, result_array, role_name)
    else:
        return result_array


def tag_role(role_name, tag_name, tag_value):
    iam_client.tag_role(
        RoleName=role_name,
        Tags=[
            {
                'Key': tag_name,
                'Value': tag_value
            },
        ]
    )


def delete_role_tags(tag_name_array, role_name):
    iam_client.untag_role(
        RoleName=role_name,
        TagKeys=tag_name_array
    )
