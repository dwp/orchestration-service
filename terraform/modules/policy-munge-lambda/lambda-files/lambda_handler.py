import json
import copy
import re

from aws_caller import list_all_policies_in_account, get_policy_statement_as_list, \
    create_policy_from_json_and_return_arn, attach_policy_to_role, \
    remove_policy_being_replaced, tag_role, get_all_role_tags, delete_role_tags

# policy json template to be copied and amended as needed
iam_template = {"Version": "2012-10-17", "Statement": []}

"""
============================================================================================================
========================================== Policy Munging Lambda ===========================================

The lambda is used to munge policies together, in order to allow larger numbers of policies to be assigned 
to a single IAM role than would otherwise be possible.

Inputs: 
 - user name/role name: the lambda expects an existing IAM role with the same name as the user and this 
 should be passed in as a root value within the event under the 'userName' key
 - policy array: an array of existing policy names that are to have their permissions combined key: 'policyArray'.
 
 Process: 
 - All existing AWS policies are collected into a list, the list is filtered to find the policies in the 
 input array.
 - The lambda sets up an array of objects, one for each policy name provided in the policy array input:
 [
    {
        "policy_name": <String: name of existing policy>,
        "statement": <Array/List: JSON IAM Statment objects>,
        "chars": <Int: number of chars in the statement JSON>,
        "chunk_number": <Int: number relating to which policy this object will be munged into>
    }, 
    ...
 ]
 - The policy statements are munged into a new JSON policy, up to the max char limit dictated by AWS. If 
 the policy reaches the limit, a new policy is started. The JSON is stored in a dict with its name as the key
 - The policies are created
 - The policies are attached to the existing role passed in as an input.
 - The role is tagged with the names of the input policies for visability - the tags undergo a similar 
 process to the policy JSON to ensure they don't hit their char limit. 
 
============================================================================================================
"""


def lambda_handler(event, context):
    user_name = event['userName']
    policy_array = event['policyArray']

    array_of_policy_objects = create_policy_object_array_from_policy_name_array(policy_array)

    dict_of_policy_name_to_munged_policy_objects = chunk_policies_and_return_dict_of_policy_name_to_json(array_of_policy_objects, user_name)

    remove_existing_user_policies(user_name)
    list_of_policy_arns = create_policies_from_dict_and_return_list_of_policy_arns(dict_of_policy_name_to_munged_policy_objects)

    attach_policies_to_role(list_of_policy_arns, user_name)
    delete_tags(user_name)
    tag_role_with_policies(policy_array, user_name)


"""
============================================================================================================
======================================== Helper methods for handler ========================================
============================================================================================================
"""


# gets list of all policies available then creates a map of policy name to statement json based on requested policies
def create_policy_object_array_from_policy_name_array(names):
    policy_object_array = []
    returned_policies = list_all_policies_in_account()
    for policy in returned_policies:
        if (policy['PolicyName'] in names):
            statement = get_policy_statement_as_list(policy['Arn'], policy['DefaultVersionId'])
            policy_object_array.append(
                {
                    'policy_name': policy['PolicyName'],
                    'statement': statement,
                    'chars': len(json.dumps(statement)),
                    'chunk_number': None
                }
            )
    verify_policies(names, policy_object_array)
    return policy_object_array


# checks original input against map used for policy
def verify_policies(names, array_of_policy_objects):
    if len(names) > len(array_of_policy_objects):
        raise Exception("Policy missing from Map.")
    # if(not policy['policy_name'] in array_of_policy_objects for policy in array_of_policy_objects):
    #         raise Exception("Policy missing from Map.")


# creates json of policy documents mapped to their policy name using iam_policy_template and statements
# from existing policies.
def chunk_policies_and_return_dict_of_policy_name_to_json(policy_object_array, user_name):
    chars_in_empty_iam_template = 42
    char_limit_of_json_policy = 6144
    policy_object_array = assign_chunk_number_to_objects(policy_object_array, chars_in_empty_iam_template,
                                                         char_limit_of_json_policy)
    total_number_of_chunks = policy_object_array[(len(policy_object_array) - 1)]['chunk_number'] + 1
    dict_of_policy_name_to_munged_policy_objects = {}
    for policy in policy_object_array:
        file_name = f'{user_name}-{policy["chunk_number"] + 1}of{total_number_of_chunks}'
        if file_name in dict_of_policy_name_to_munged_policy_objects:
            dict_of_policy_name_to_munged_policy_objects[file_name]['Statement'].extend(policy['statement'])
        else:
            iam_policy = copy.deepcopy(iam_template)
            iam_policy['Statement'] = policy['statement']
            dict_of_policy_name_to_munged_policy_objects[file_name] = iam_policy

    # checks to see if 20 policy attachment limit is reached before creating policies
    if (len(dict_of_policy_name_to_munged_policy_objects) > 20):
        raise Exception(f"Maximum policy assignment exceeded for role: {user_name}")

    return dict_of_policy_name_to_munged_policy_objects


# fills chunk_number attribute of object based on AWS imposed character allowance
def assign_chunk_number_to_objects(object_array, start_char, max_char):
    count = 0
    chars = start_char
    print(f'this is tha policy object array: {object_array}')
    for object in object_array:
        print(f'this it the policy_object value: {object}')
        if (chars + object['chars']) >= max_char:
            count += 1
            chars = start_char
        object['chunk_number'] = count
        chars += object['chars']
        print(f'current chars: {chars}')
    return object_array


# deletes any existing policies made by the lambda to apply the fresh set to user
def remove_existing_user_policies(user_name):
    returned_policies = list_all_policies_in_account()
    regex = re.compile(f"{user_name}-\d*of\d*")
    for policy in returned_policies:
        if (regex.match(policy['PolicyName'])):
            remove_policy_being_replaced(policy['Arn'], user_name)


# creates policies in IAM from JSON files and removes JSON files
def create_policies_from_dict_and_return_list_of_policy_arns(dict_of_policy_name_to_munged_policy_objects):
    list_of_policy_arns = []
    for policy in dict_of_policy_name_to_munged_policy_objects:
        policy_arn = create_policy_from_json_and_return_arn(policy, json.dumps(dict_of_policy_name_to_munged_policy_objects[policy]))
        list_of_policy_arns.append(policy_arn)
    return list_of_policy_arns


def attach_policies_to_role(list_of_policy_arns, user_name):
    for arn in list_of_policy_arns:
        attach_policy_to_role(arn, user_name)



def delete_tags(role_name):
    tags = get_all_role_tags(role_name)
    regex = re.compile(f"InputPolicies-\d*of\d*")
    tag_name_array = []
    for tag in tags:
        if (regex.match(tag['Key'])):
            tag_name_array.append(tag['Key'])
    if (len(tag_name_array) > 0):
        delete_role_tags(tag_name_array, role_name)


# creates tag values mapped to their tag name to avoid hitting the maximum tag per role
def tag_role_with_policies(policy_array, role_name):
    tag_object_array = []
    separator = '/'
    chars_in_empty_tag = 0
    char_limit_for_tag_value = 200
    for name in policy_array:
        tag_object_array.append(
            {
                "policy_name": name,
                "chars": len(name),
                "chunk_number": None
            }
        )
    chunked_tag_object_array = assign_chunk_number_to_objects(tag_object_array,
                                                              chars_in_empty_tag,
                                                              char_limit_for_tag_value)
    tag_keys_to_value_list = {}
    total_number_of_chunks = chunked_tag_object_array[(len(chunked_tag_object_array) - 1)]['chunk_number'] + 1
    for tag in chunked_tag_object_array:
        tag_key = f'InputPolicies-{tag["chunk_number"] + 1}of{total_number_of_chunks}'
        if tag_key in tag_keys_to_value_list:
            tag_keys_to_value_list[tag_key].append(tag['policy_name'])
        else:
            tag_keys_to_value_list[tag_key] = [tag['policy_name']]

    if len(tag_keys_to_value_list) > 50:
        raise Exception("Tag limit for role exceeded")

    for tag in tag_keys_to_value_list:
        tag_value = separator.join(tag_keys_to_value_list[tag])
        tag_role(role_name, tag, tag_value)
