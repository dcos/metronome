#
#    Copyright (C) 2015 Mesosphere, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import sys


from dcos import http, util
from dcos.errors import DCOSHTTPException

__fwk = None


def get_fwk_name():
    return __fwk or util.get_config().get('metronome.service_name') or "metronome"  # noqa


def set_fwk_name(name):
    global __fwk
    __fwk = name


def base_url():
    return util.get_config().get('core.dcos_url').rstrip("/")


def marathon_url(slash_command):
    return "%s/marathon/v2%s" % (base_url(), slash_command)


def http_delete_json(slash_command, params=None):
    __http_request_json('delete', slash_command, params)


def http_get_json(slash_command, params=None, ignored_errors=[]):
    __http_request_json(
        'get', slash_command, params, ignored_errors=ignored_errors)


def http_post_json(slash_command, params=None):
    __http_request_json('post', slash_command, params)


def http_put_json(slash_command, params=None):
    __http_request_json('put', slash_command, params)


def __http_request_json(
        method, slash_command, params, ignored_errors=[], exit_on_fail=True):
    request_url = __api_url(slash_command)
    try:
        response = http.request(method, request_url, headers={}, params=params)
        response.raise_for_status()
        json_obj = response.json()
    except DCOSHTTPException as e:
        if e.status() in ignored_errors:
            # Expected. Return response without complaint.
            json_obj = e.response.json()
        else:
            # Not expected. Print error.
            print("Failed to %s %s" % (method.upper(), request_url))
            print("HTTP %s: %s" % (e.status(), e.response.reason))
            print("Content: %s" % (e.response.content))
            if exit_on_fail:
                sys.exit(1)
            return

    if json_obj:
        print(json.dumps(
            json_obj, sort_keys=True, indent=4, separators=(',', ': ')))
    else:
        print("No JSON returned for %s %s" % (method.upper(), request_url))
        if exit_on_fail:
            sys.exit(1)


def __api_url(slash_command):
    base_config_url = util.get_config().get('metronome.url')
    if base_config_url is not None:
        base_config_url = base_config_url.rstrip("/")
    else:
        base_config_url = "%s/service/%s" % (base_url(), get_fwk_name())
    return "%s/v1%s" % (base_config_url, slash_command)
