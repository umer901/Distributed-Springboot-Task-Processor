*** Settings ***
Documentation     End-to-end API tests for the distributed task processor.
Library           RequestsLibrary
Library           Collections
Library           String
Suite Setup       Create Session    task_api    ${BASE_URL}

*** Variables ***
${BASE_URL}       %{BASE_URL=http://localhost:8080}

*** Test Cases ***
Health Endpoint Is Ready
    ${response}=    GET On Session    task_api    /actuator/health    expected_status=200
    Should Contain    ${response.text}    UP

Create Checksum Task And Poll To Success
    ${task_id}=    Create Task    CHECKSUM    text=hello    timeout_seconds=10
    ${task}=    Wait For Task Status    ${task_id}    SUCCEEDED
    Dictionary Should Contain Item    ${task}    status    SUCCEEDED
    ${result}=    Get From Dictionary    ${task}    result
    Dictionary Should Contain Item    ${result}    algorithm    SHA-256
    Dictionary Should Contain Item    ${result}    checksum    2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824

Idempotency Key Replays Same Request
    ${key}=    Generate Random String    16    [LOWER]
    &{payload}=    Create Dictionary    text=same
    &{body}=    Create Dictionary    taskType=CHECKSUM    payload=${payload}    maxAttempts=${3}    timeoutSeconds=${10}
    ${first}=    Post Task Body    ${key}    ${body}    201
    ${second}=    Post Task Body    ${key}    ${body}    200
    ${first_json}=    Set Variable    ${first.json()}
    ${second_json}=    Set Variable    ${second.json()}
    ${first_id}=    Get From Dictionary    ${first_json}    id
    ${second_id}=    Get From Dictionary    ${second_json}    id
    Should Be Equal    ${first_id}    ${second_id}
    Dictionary Should Contain Item    ${second_json}    idempotentReplay    ${True}

Idempotency Key Rejects Different Request
    ${key}=    Generate Random String    16    [LOWER]
    &{first_payload}=    Create Dictionary    text=first
    &{second_payload}=    Create Dictionary    text=second
    &{first_body}=    Create Dictionary    taskType=CHECKSUM    payload=${first_payload}
    &{second_body}=    Create Dictionary    taskType=CHECKSUM    payload=${second_payload}
    Post Task Body    ${key}    ${first_body}    201
    ${conflict}=    Post Task Body    ${key}    ${second_body}    409
    Should Contain    ${conflict.text}    Idempotency key conflict

Invalid Request Returns Problem Detail
    ${key}=    Generate Random String    16    [LOWER]
    &{body}=    Create Dictionary    taskType=    payload=${None}
    ${response}=    Post Task Body    ${key}    ${body}    400
    Should Contain    ${response.text}    Invalid request body

Prometheus Metrics Endpoint Is Exposed
    ${response}=    GET On Session    task_api    /actuator/prometheus    expected_status=200
    Should Contain    ${response.text}    task_processor_outbox_pending

*** Keywords ***
Create Task
    [Arguments]    ${task_type}    ${payload_pair}    ${timeout_seconds}=10
    ${key}=    Generate Random String    16    [LOWER]
    ${payload_key}    ${payload_value}=    Split String    ${payload_pair}    =
    &{payload}=    Create Dictionary    ${payload_key}=${payload_value}
    &{body}=    Create Dictionary    taskType=${task_type}    payload=${payload}    maxAttempts=${3}    timeoutSeconds=${timeout_seconds}
    ${response}=    Post Task Body    ${key}    ${body}    201
    ${json}=    Set Variable    ${response.json()}
    ${task_id}=    Get From Dictionary    ${json}    id
    RETURN    ${task_id}

Post Task Body
    [Arguments]    ${idempotency_key}    ${body}    ${expected_status}
    &{headers}=    Create Dictionary    Content-Type=application/json    Idempotency-Key=${idempotency_key}
    ${response}=    POST On Session    task_api    /api/v1/tasks    json=${body}    headers=${headers}    expected_status=${expected_status}
    RETURN    ${response}

Wait For Task Status
    [Arguments]    ${task_id}    ${expected_status}
    ${task}=    Wait Until Keyword Succeeds    30x    500ms    Task Should Have Status    ${task_id}    ${expected_status}
    RETURN    ${task}

Task Should Have Status
    [Arguments]    ${task_id}    ${expected_status}
    ${response}=    GET On Session    task_api    /api/v1/tasks/${task_id}    expected_status=200
    ${task}=    Set Variable    ${response.json()}
    ${status}=    Get From Dictionary    ${task}    status
    Should Be Equal    ${status}    ${expected_status}
    RETURN    ${task}
