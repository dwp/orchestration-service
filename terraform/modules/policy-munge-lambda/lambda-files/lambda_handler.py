import json
import copy
import os
import re

from aws_caller import list_policies, get_policy_statement, create_policy_from_json, attach_policy_to_role, \
    remove_old_policy, tag_role, get_role_tags, delete_role_tags

# policy json template to be copied and amended as needed
iam_template = {"Version": "2012-10-17", "Statement": []}


# checks original input against map used for policy
def verify_policies(names, map_of_names_to_policies):
    for policy_name in names:
        if (not map_of_names_to_policies[policy_name]):
            raise Exception(f"Policy called: {policy_name} not found in map.")


# gets list of all policies available then creates a map of policy name to statement json based on requested policies
def get_policy_documents_by_name(names):
    policy_name_to_document = {}
    returned_policies = list_policies()
    for policy in returned_policies:
        if (policy['PolicyName'] in names):
            document = get_policy_statement(policy['Arn'], policy['DefaultVersionId'])
            policy_name_to_document[policy['PolicyName']] = document
    verify_policies(names, policy_name_to_document)
    return policy_name_to_document


# deletes any existing policies made by the lambda to apply the fresh set to user
def remove_existing_user_policies(user_name):
    returned_policies = list_policies()
    regex = re.compile(f"{user_name}-\d*of\d*")
    for policy in returned_policies:
        if (regex.match(policy['PolicyName'])):
            remove_old_policy(policy['Arn'], user_name)


# gets length of statement json in chars
def get_chars_in_document(policy_name_to_document):
    policy_name_to_chars = {}
    for policy in policy_name_to_document:
        policy_name_to_chars[policy] = len(json.dumps(policy_name_to_document[policy]))
    return policy_name_to_chars


# creates map of count of files to produce to policy documents to add to each file
def get_chunk_contents(policy_name_to_chars, start_char, max_char):
    count = 1
    # number of chars already in iam_template
    # chars = 42
    chars = start_char
    dict_of_chunks = {count: []}
    for policy in policy_name_to_chars:
        # if ( (chars+policy_name_to_chars[policy]) >= 6144 ):
        if ((chars + policy_name_to_chars[policy]) >= max_char):
            count += 1
            dict_of_chunks[count] = []
            # chars = 42
            chars = start_char
        dict_of_chunks[count].append(policy)
        chars += policy_name_to_chars[policy]
        print(f'current chars: {chars}')
        print(dict_of_chunks)
    return dict_of_chunks


# creates json files locally (/tmp/) that contain policy documents
def chunk_policies(policy_name_to_document, policy_name_to_chars, user_name):
    chunk_contents = get_chunk_contents(policy_name_to_chars, 42, 6144)
    for chunk in chunk_contents:
        file_name = f'{user_name}-{chunk}of{len(chunk_contents)}'
        iam_policy = copy.deepcopy(iam_template)
        for policy_name in chunk_contents[chunk]:
            iam_policy['Statement'].extend(policy_name_to_document[policy_name])
        with open(f'/tmp/{file_name}.json', 'a') as outfile:
            json.dump(iam_policy, outfile)


# creates policies in IAM from JSON files and removes JSON files
def create_policies_from_files(user_name):
    arn_array = []
    file_array = []
    regex = re.compile(f"{user_name}-\d*of\d*\.json")
    for file in os.listdir("/tmp/"):
        if (regex.match(file)):
            file_array.append(file)

    # checks to see if 20 policy attachment limit is reached before creating policies
    if (len(file_array) > 20):
        raise Exception(f"Maximum policy assignment exceeded for role: {user_name}")

    for file in file_array:
        policy_name = file[:-5]
        with open(f'/tmp/{file}') as f: json_document = f.read()
        print(json_document)
        policy_arn = create_policy_from_json(policy_name, json_document)
        arn_array.append(policy_arn)
        delete_local_file(f'/tmp/{file}')
    return arn_array


def attach_policies_to_role(arn_array, user_name):
    for arn in arn_array:
        attach_policy_to_role(arn, user_name)


def delete_local_file(file):
    os.remove(file)


def delete_tags(role_name):
    tags = get_role_tags(role_name)
    regex = re.compile(f"InputPolicies-\d*of\d*")
    tag_name_array = []
    for tag in tags:
        if (regex.match(tag['Key'])):
            tag_name_array.append(tag['Key'])
    if (len(tag_name_array) > 0):
        delete_role_tags(tag_name_array, role_name)


def tag_role_with_policies(policy_array, role_name):
    name_to_chars = {}
    separator = '/'
    for name in policy_array:
        name_to_chars[name] = len(name)
    chunked_tags = get_chunk_contents(name_to_chars, 0, 200)
    for tag in chunked_tags:
        value = separator.join(chunked_tags[tag])
        tag_role(role_name, f'InputPolicies-{tag}of{len(chunked_tags)}', value)


def lambda_handler(event, context):
    user_name = event['userName']
    policy_array = event['policyArray']

    name_to_statement = get_policy_documents_by_name(policy_array)
    name_to_chars = get_chars_in_document(name_to_statement)

    chunk_policies(name_to_statement, name_to_chars, user_name)

    remove_existing_user_policies(user_name)
    arn_array = create_policies_from_files(user_name)

    attach_policies_to_role(arn_array, user_name)
    delete_tags(user_name)
    tag_role_with_policies(policy_array, user_name)
