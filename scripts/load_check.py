#!/usr/bin/env python3
"""Checks that the cluster enforces one shared limit across all gateway instances.

Sends requests with a single API key through the load balancer from many threads and compares the number of
accepted requests with what the limit allows (capacity + refill * elapsed seconds).

Usage: scripts/load_check.py [--url http://localhost:8000/api/] [--requests 500] [--threads 32]
       [--capacity 10] [--refill-per-second 1]
"""
import argparse
import collections
import concurrent.futures
import time
import urllib.error
import urllib.request


def call(url, key):
    request = urllib.request.Request(url, headers={"X-API-Key": key})
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, response.headers.get("X-Upstream", "?")
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("X-Upstream", "?")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8000/api/")
    parser.add_argument("--requests", type=int, default=500)
    parser.add_argument("--threads", type=int, default=32)
    parser.add_argument("--capacity", type=int, default=10)
    parser.add_argument("--refill-per-second", type=float, default=1.0)
    args = parser.parse_args()

    key = "load-check-%d" % time.time()
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(args.threads) as pool:
        results = list(pool.map(lambda _: call(args.url, key), range(args.requests)))
    elapsed = time.monotonic() - started

    statuses = collections.Counter(status for status, _ in results)
    instances = collections.Counter(upstream for _, upstream in results)
    accepted = statuses.get(200, 0)
    allowed = args.capacity + args.refill_per_second * elapsed

    print("requests: %d in %.2fs (%.0f req/s)" % (args.requests, elapsed, args.requests / elapsed))
    print("statuses: %s" % dict(statuses))
    print("served by %d gateway instances: %s" % (len(instances), dict(instances)))
    print("accepted %d, limit allows about %.1f" % (accepted, allowed))
    # One extra token of slack for refill timing at the edges.
    if accepted > allowed + 1:
        raise SystemExit("FAIL: more requests were accepted than the shared limit allows")
    print("OK: the limit was shared across instances")


if __name__ == "__main__":
    main()
