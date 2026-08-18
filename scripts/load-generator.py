#!/usr/bin/env python3
import argparse
import json
import time
import urllib.error
import urllib.request
import uuid


def post_json(base_url, path, body, headers):
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        base_url + path,
        data=data,
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def get_json(base_url, path):
    with urllib.request.urlopen(base_url + path, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(description="Submit sample tasks and poll their final states.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--task-type", choices=["CHECKSUM", "DELAY"], default="CHECKSUM")
    parser.add_argument("--delay-ms", type=int, default=100)
    parser.add_argument("--timeout-seconds", type=int, default=10)
    args = parser.parse_args()

    task_ids = []
    for index in range(args.count):
        payload = {"text": f"load-{index}"} if args.task_type == "CHECKSUM" else {"millis": args.delay_ms}
        body = {
            "taskType": args.task_type,
            "payload": payload,
            "maxAttempts": 3,
            "timeoutSeconds": args.timeout_seconds,
        }
        key = f"load-{uuid.uuid4()}"
        _, response = post_json(args.base_url, "/api/v1/tasks", body, {"Idempotency-Key": key})
        task_ids.append(response["id"])
    print(f"Submitted {len(task_ids)} tasks")

    deadline = time.time() + 60
    while time.time() < deadline:
        statuses = {}
        for task_id in task_ids:
            task = get_json(args.base_url, f"/api/v1/tasks/{task_id}")
            statuses[task["status"]] = statuses.get(task["status"], 0) + 1
        print(statuses)
        if sum(statuses.get(status, 0) for status in ["SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT"]) == len(task_ids):
            return
        time.sleep(1)
    raise SystemExit("Timed out waiting for all tasks to become terminal")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        print(error.read().decode("utf-8"))
        raise
