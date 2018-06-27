---
title: Docker Run Configurations
---

# Docker

It is possible now to run jobs using docker's privilege mode and/or using docker run time parameters.  These can be used to control the [docker runtime privileges](https://docs.docker.com/engine/reference/run/#runtime-privilege-and-linux-capabilities).

## Privileged Mode

Running docker in privileged mode is possible by setting `privileged=true` in the docker section of the job definition.

```
{
  "description": "example docker that runs in privilege mode",
  "id": "docker-priv",
  "run": {
    "cmd": "sleep inf",
    "cpus": 0.2,
    "mem": 32,
    "docker": {
      "image": "ubuntu",
      "privileged": true
    }
  }
}
```

## Docker Parameters

It is possible to set docker parameters now with job runs.   This enables the ability to change runtime capabilities of the job.   The example below removes all the default docker capabilities and adds SYSLOG.

```
{
  "description": "example docker that changes runtime capabilities",
  "id": "docker-param",
  "run": {
    "cmd": "sleep inf",
    "cpus": 0.2,
    "mem": 32,
    "docker": {
      "image": "ubuntu",
      "parameters": [
        {
          "key": "cap-drop",
          "value": "ALL"
        },
        {
          "key": "cap-add",
          "value": "SYSLOG"
        }
      ]
    }
  }
}
```
